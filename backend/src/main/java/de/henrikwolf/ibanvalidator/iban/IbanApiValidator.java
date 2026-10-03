package de.henrikwolf.ibanvalidator.iban;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Validates IBANs with the external service ibanapi.com (endpoint {@code validate-basic}, 1 credit per call).
 * The API key is sent as {@code Authorization} header, see {@link IbanApiConfiguration}.
 */
@Service
public class IbanApiValidator implements IbanValidator {

    private static final int OK = 200;

    private final RestClient ibanApiRestClient;

    public IbanApiValidator(RestClient ibanApiRestClient) {
        this.ibanApiRestClient = ibanApiRestClient;
    }

    @Override
    public IbanValidationResult validate(String iban) {
        IbanApiResponse response = call(iban);
        // Only a response with individual checks is a verdict on the IBAN; anything else (invalid key,
        // exhausted balance, ...) means the service cannot answer.
        if (response == null || response.validations() == null || response.validations().isEmpty()) {
            throw new ValidatorUnavailableException("ibanapi.com gave no verdict: "
                    + (response == null ? "empty response" : response.result() + " " + response.message()));
        }
        if (response.result() == OK) {
            return IbanValidationResult.valid(iban, countryCodeOf(iban, response));
        }

        String message = response.validations().stream()
                .filter(check -> check.result() != OK)
                .map(Check::message)
                .collect(Collectors.joining("; "));
        return IbanValidationResult.invalid(iban, message.isEmpty() ? response.message() : message);
    }

    private static String countryCodeOf(String iban, IbanApiResponse response) {
        // Prefer the provider's country code, fall back to the first two characters of the IBAN.
        if (response.data() != null && response.data().countryCode() != null) {
            return response.data().countryCode();
        }
        return iban.substring(0, 2);
    }

    private IbanApiResponse call(String iban) {
        try {
            // exchange() instead of retrieve(): invalid IBANs are answered with 4xx and still carry a body.
            return ibanApiRestClient.get()
                    .uri("/validate-basic/{iban}", iban)
                    .exchange((request, response) -> response.bodyTo(IbanApiResponse.class));
        } catch (RestClientException e) {
            // Do not include the exception message: it carries the request URL incl. the IBAN.
            throw new ValidatorUnavailableException("ibanapi.com is not reachable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IbanApiResponse(int result, String message, List<Check> validations, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Check(int result, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(@JsonProperty("country_code") String countryCode) {
    }
}

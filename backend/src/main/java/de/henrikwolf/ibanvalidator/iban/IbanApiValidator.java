package de.henrikwolf.ibanvalidator.iban;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Validates IBANs with the external service ibanapi.com. Each call costs one credit of the endpoint's balance
 * (basic or bank). The API key is sent as {@code Authorization} header, see {@link IbanApiConfiguration}.
 */
public class IbanApiValidator implements IbanValidator {

    private static final int OK = 200;

    /** ibanapi.com endpoints; {@code EXTENDED} additionally returns bank data. */
    public enum Endpoint {
        BASIC("/validate-basic/{iban}"),
        EXTENDED("/validate/{iban}");

        private final String uri;

        Endpoint(String uri) {
            this.uri = uri;
        }
    }

    private final RestClient ibanApiRestClient;
    private final Endpoint endpoint;

    public IbanApiValidator(RestClient ibanApiRestClient, Endpoint endpoint) {
        this.ibanApiRestClient = ibanApiRestClient;
        this.endpoint = endpoint;
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
            Data data = response.data();
            return IbanValidationResult.valid(iban, countryCodeOf(iban, data),
                    data != null ? data.countryName() : null,
                    bankValue(data, "bank_name"), bankValue(data, "bic"));
        }

        String message = response.validations().stream()
                .filter(check -> check.result() != OK)
                .map(Check::message)
                .collect(Collectors.joining("; "));
        return IbanValidationResult.invalid(iban, message.isEmpty() ? response.message() : message);
    }

    private static String countryCodeOf(String iban, Data data) {
        // Prefer the provider's country code, fall back to the first two characters of the IBAN.
        if (data != null && data.countryCode() != null) {
            return data.countryCode();
        }
        return iban.substring(0, 2);
    }

    // The provider sends an empty array instead of an object if nothing is known, and empty strings for unknown values.
    private static String bankValue(Data data, String key) {
        if (data != null && data.bank() instanceof Map<?, ?> bank && bank.get(key) instanceof String value
                && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private IbanApiResponse call(String iban) {
        try {
            // exchange() instead of retrieve(): invalid IBANs are answered with 4xx and still carry a body.
            return ibanApiRestClient.get()
                    .uri(endpoint.uri, iban)
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
    record Data(@JsonProperty("country_code") String countryCode,
                @JsonProperty("country_name") String countryName,
                Object bank) {
    }
}
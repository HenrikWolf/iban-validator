package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class IbanApiValidatorTest {

    private static final String BASE_URL = "https://ibanapi.test/v1";
    private static final String IBAN = "DE89370400440532013000";

    private MockRestServiceServer server;
    private IbanApiValidator validator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        validator = new IbanApiValidator(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void acceptsValidIban() {
        expectCall(IBAN).andRespond(json(HttpStatus.OK, """
                {"result": 200, "message": "Valid IBAN Number",
                 "validations": [{"result": 200, "message": "Valid IBAN length"},
                                 {"result": 200, "message": "Valid IBAN Checksum"},
                                 {"result": 200, "message": "Valid IBAN Structure"}],
                 "expremental": 0, "data": {"country_code": "DE", "sepa": {}}}
                """));

        IbanValidationResult result = validator.validate(IBAN);

        assertThat(result.isValid()).isTrue();
        assertThat(result.countryCode()).isEqualTo("DE");
        assertThat(result.failureMessage()).isNull();
    }

    @Test
    void mapsWrongChecksum() {
        expectCall(IBAN).andRespond(json(HttpStatus.BAD_REQUEST, """
                {"result": 400, "message": "Invalid IBAN Number",
                 "validations": [{"result": 200, "message": "Valid IBAN length"},
                                 {"result": 414, "message": "Invalid IBAN Checksum"},
                                 {"result": 200, "message": "Valid IBAN Structure"}],
                 "data": {"country_code": "DE"}}
                """));

        IbanValidationResult result = validator.validate(IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("Invalid IBAN Checksum");
    }

    @Test
    void joinsMessagesOfAllFailedChecks() {
        expectCall(IBAN).andRespond(json(HttpStatus.BAD_REQUEST, """
                {"result": 400, "message": "Invalid IBAN Number",
                 "validations": [{"result": 413, "message": "Invalid IBAN number length"},
                                 {"result": 414, "message": "Invalid IBAN Checksum"},
                                 {"result": 415, "message": "Invalid IBAN Structure"}]}
                """));

        assertThat(validator.validate(IBAN).failureMessage())
                .isEqualTo("Invalid IBAN number length; Invalid IBAN Checksum; Invalid IBAN Structure");
    }

    @Test
    void returnsMessageForUnsupportedCountry() {
        expectCall(IBAN).andRespond(json(HttpStatus.PRECONDITION_FAILED, """
                {"result": 412, "message": "This country does not support IBAN",
                 "validations": [{"result": 412, "message": "This country does not support IBAN"}],
                 "data": {}}
                """));

        assertThat(validator.validate(IBAN).failureMessage()).isEqualTo("This country does not support IBAN");
    }

    @Test
    void encodesIbanInPath() {
        expectCall("DE89%2F..%2FX").andRespond(json(HttpStatus.BAD_REQUEST, """
                {"result": 400, "message": "Invalid IBAN Number",
                 "validations": [{"result": 415, "message": "Invalid IBAN Structure"}]}
                """));

        assertThat(validator.validate("DE89/../X").failureMessage()).isEqualTo("Invalid IBAN Structure");
    }

    @Test
    void isUnavailableIfServiceRejectsKey() {
        expectCall(IBAN).andRespond(json(HttpStatus.UNAUTHORIZED, """
                {"result": 401, "message": "Invalid API Key", "data": {}}
                """));

        assertThatThrownBy(() -> validator.validate(IBAN))
                .isInstanceOf(ValidatorUnavailableException.class)
                .hasMessageContaining("Invalid API Key");
    }

    @Test
    void isUnavailableIfServiceIsNotReachable() {
        expectCall(IBAN).andRespond(withException(new IOException("connect timed out")));

        assertThatThrownBy(() -> validator.validate(IBAN)).isInstanceOf(ValidatorUnavailableException.class);
    }

    @Test
    void isUnavailableIfResponseIsNotJson() {
        expectCall(IBAN).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML)
                .body("<html>Bad Gateway</html>"));

        assertThatThrownBy(() -> validator.validate(IBAN)).isInstanceOf(ValidatorUnavailableException.class);
    }

    private ResponseActions expectCall(String encodedIban) {
        return server.expect(requestTo(BASE_URL + "/validate-basic/" + encodedIban))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "test-key"));
    }

    private static DefaultResponseCreator json(HttpStatus status, String body) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}

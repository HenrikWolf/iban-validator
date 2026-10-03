package de.henrikwolf.ibanvalidator.iban;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(IbanController.class)
@Import({IbanNormalizer.class, InternalIbanValidator.class})
class IbanControllerTest {

    private static final String URL = "/api/v1/iban/validation";
    private static final String IBAN = "DE89370400440532013000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IbanApiValidator ibanApiValidator;

    @MockitoBean
    private IbanRepository ibanRepository;

    @Test
    void usesExternalValidatorByDefaultWithNormalizedIban() throws Exception {
        when(ibanApiValidator.validate(IBAN)).thenReturn(IbanValidationResult.valid(IBAN, "DE"));

        post("""
                {"iban": "de89 3704-0044.0532 0130 00"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(content().string(not(containsString("failureMessage"))));

        verify(ibanRepository).saveIfAbsent(IBAN);
    }

    @Test
    void returnsMessageOfExternalValidator() throws Exception {
        when(ibanApiValidator.validate(IBAN)).thenReturn(
                IbanValidationResult.invalid(IBAN, "Invalid IBAN Checksum"));

        post("""
                {"iban": "%s", "validator": "IBANAPI"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.failureMessage").value("Invalid IBAN Checksum"));

        verify(ibanRepository, never()).saveIfAbsent(anyString());
    }

    @Test
    void usesInternalValidatorIfSelected() throws Exception {
        post("""
                {"iban": "DE88370400440532013000", "validator": "INTERNAL"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.countryCode").doesNotExist())
                .andExpect(jsonPath("$.failureMessage").value("Invalid IBAN Checksum"));

        verify(ibanApiValidator, never()).validate(anyString());
    }

    @Test
    void storesValidIbanOfInternalValidator() throws Exception {
        post("""
                {"iban": "%s", "validator": "INTERNAL"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(ibanRepository).saveIfAbsent(IBAN);
    }

    @Test
    void returnsServiceUnavailableIfValidatorIsUnavailable() throws Exception {
        when(ibanApiValidator.validate(IBAN)).thenThrow(new ValidatorUnavailableException("down"));

        post("""
                {"iban": "%s"}
                """.formatted(IBAN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("IBAN validation is currently unavailable, please try again later."));

        verify(ibanRepository, never()).saveIfAbsent(anyString());
    }

    @Test
    void returnsResultEvenIfStoringFails() throws Exception {
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(ibanRepository).saveIfAbsent(anyString());

        post("""
                {"iban": "%s", "validator": "INTERNAL"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void rejectsUnknownValidator() throws Exception {
        post("""
                {"iban": "%s", "validator": "FOO"}
                """.formatted(IBAN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingIban() throws Exception {
        post("{}").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyIban() throws Exception {
        post("""
                {"iban": ""}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSeparatorOnlyIban() throws Exception {
        post("""
                {"iban": " -.- "}
                """)
                .andExpect(status().isBadRequest());

        verify(ibanApiValidator, never()).validate(anyString());
    }

    private ResultActions post(String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

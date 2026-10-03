package de.henrikwolf.ibanvalidator.iban;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
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

    @MockitoBean(name = "ibanApiValidator")
    private IbanApiValidator ibanApiValidator;

    @MockitoBean(name = "ibanApiExtendedValidator")
    private IbanApiValidator ibanApiExtendedValidator;

    @MockitoBean
    private IbanRepository ibanRepository;

    @MockitoBean
    private BankRepository bankRepository;

    @Test
    void usesInternalValidatorByDefaultWithNormalizedIban() throws Exception {
        when(bankRepository.saveBankCode("37040044")).thenReturn(3L);

        post("""
                {"iban": "de89 3704-0044.0532 0130 00"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.countryName").value("Germany"))
                .andExpect(content().string(not(containsString("failureMessage"))))
                .andExpect(content().string(not(containsString("bankName"))));

        verify(ibanApiValidator, never()).validate(anyString());
        verify(ibanApiExtendedValidator, never()).validate(anyString());
        verify(ibanRepository).saveIfAbsent(IBAN, 3L);
    }

    @Test
    void storesNoBankCodeForNonGermanIban() throws Exception {
        post("""
                {"iban": "GB82WEST12345698765432"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(bankRepository, never()).saveBankCode(anyString());
        verify(ibanRepository).saveIfAbsent("GB82WEST12345698765432", null);
    }

    @Test
    void usesExtendedExternalValidatorAndReturnsBankData() throws Exception {
        when(bankRepository.save(any())).thenReturn(7L);
        when(ibanApiExtendedValidator.validate(IBAN)).thenReturn(
                IbanValidationResult.valid(IBAN, "DE", "Germany", "Commerzbank", "COBADEFFXXX"));

        post("""
                {"iban": "%s", "validator": "IBANAPI_EXTENDED"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.bankName").value("Commerzbank"))
                .andExpect(jsonPath("$.bic").value("COBADEFFXXX"));

        verify(ibanApiValidator, never()).validate(anyString());
        verify(bankRepository).save(new Bank("COBADEFFXXX", "Commerzbank", "Germany", "37040044"));
        verify(ibanRepository).saveIfAbsent(IBAN, 7L);
    }

    @Test
    void usesExternalValidatorIfSelected() throws Exception {
        when(ibanApiValidator.validate(IBAN)).thenReturn(IbanValidationResult.valid(IBAN, "DE"));

        post("""
                {"iban": "de89 3704 0044 0532 0130 00", "validator": "IBANAPI"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.countryCode").value("DE"));

        verify(ibanApiValidator).validate(IBAN);
        verify(ibanApiExtendedValidator, never()).validate(anyString());
        verify(bankRepository).saveBankCode("37040044");
    }

    @Test
    void handlesGermanResultTooShortForBankCode() throws Exception {
        String shortIban = "DE89370400";
        when(ibanApiValidator.validate(shortIban)).thenReturn(IbanValidationResult.valid(shortIban, "DE"));

        post("""
                {"iban": "%s", "validator": "IBANAPI"}
                """.formatted(shortIban))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(bankRepository, never()).saveBankCode(anyString());
        verify(ibanRepository).saveIfAbsent(shortIban, null);
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

        verify(ibanRepository, never()).saveIfAbsent(anyString(), any());
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
    void returnsServiceUnavailableIfValidatorIsUnavailable() throws Exception {
        when(ibanApiValidator.validate(IBAN)).thenThrow(new ValidatorUnavailableException("down"));

        post("""
                {"iban": "%s", "validator": "IBANAPI"}
                """.formatted(IBAN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("IBAN validation is currently unavailable, please try again later."));

        verify(ibanRepository, never()).saveIfAbsent(anyString(), any());
    }

    @Test
    void returnsResultEvenIfStoringFails() throws Exception {
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(ibanRepository).saveIfAbsent(anyString(), any());

        post("""
                {"iban": "%s", "validator": "INTERNAL"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void returnsStoredBankDataForOtherValidators() throws Exception {
        when(ibanRepository.findBank(IBAN)).thenReturn(
                Optional.of(new Bank("COBADEFFXXX", "Commerzbank", "Germany", "50040000")));
        when(ibanApiValidator.validate(IBAN)).thenReturn(IbanValidationResult.valid(IBAN, "DE"));

        for (String validator : new String[] {"INTERNAL", "IBANAPI"}) {
            post("""
                    {"iban": "%s", "validator": "%s"}
                    """.formatted(IBAN, validator))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value("Commerzbank"))
                    .andExpect(jsonPath("$.bic").value("COBADEFFXXX"));
        }

        verify(bankRepository, never()).save(any());
        verify(bankRepository, never()).saveBankCode(anyString());
        verify(ibanRepository, times(2)).saveIfAbsent(IBAN, null);
    }

    @Test
    void linksGermanBankCodeAndReturnsItsBankData() throws Exception {
        when(bankRepository.saveBankCode("37040044")).thenReturn(3L);
        when(ibanRepository.findBank(IBAN)).thenReturn(Optional.empty(),
                Optional.of(new Bank("COBADEFFXXX", "Commerzbank", "Germany", "37040044")));

        post("""
                {"iban": "%s"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Commerzbank"))
                .andExpect(jsonPath("$.bic").value("COBADEFFXXX"));

        verify(ibanRepository).saveIfAbsent(IBAN, 3L);
    }

    @Test
    void returnsBankDataFromValidatorEvenIfStoringFails() throws Exception {
        when(ibanApiExtendedValidator.validate(IBAN)).thenReturn(
                IbanValidationResult.valid(IBAN, "DE", "Germany", "Commerzbank", "COBADEFFXXX"));
        when(bankRepository.save(any())).thenThrow(new DataAccessResourceFailureException("database down"));

        post("""
                {"iban": "%s", "validator": "IBANAPI_EXTENDED"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bic").value("COBADEFFXXX"));
    }

    @Test
    void storesForeignBankWithoutBankCodeForExtendedValidator() throws Exception {
        String foreignIban = "GB82WEST12345698765432";
        when(bankRepository.save(any())).thenReturn(9L);
        when(ibanApiExtendedValidator.validate(foreignIban)).thenReturn(
                IbanValidationResult.valid(foreignIban, "GB", "United Kingdom", "NatWest", "NWBKGB2LXXX"));

        post("""
                {"iban": "%s", "validator": "IBANAPI_EXTENDED"}
                """.formatted(foreignIban))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("NatWest"))
                .andExpect(jsonPath("$.bic").value("NWBKGB2LXXX"));

        verify(bankRepository).save(new Bank("NWBKGB2LXXX", "NatWest", "United Kingdom", null));
        verify(bankRepository, never()).saveBankCode(anyString());
        verify(ibanRepository).saveIfAbsent(foreignIban, 9L);
    }

    @Test
    void seedsGermanBankCodeWhenExtendedValidatorReturnsNoBankData() throws Exception {
        when(bankRepository.saveBankCode("37040044")).thenReturn(3L);
        when(ibanApiExtendedValidator.validate(IBAN)).thenReturn(IbanValidationResult.valid(IBAN, "DE"));

        post("""
                {"iban": "%s", "validator": "IBANAPI_EXTENDED"}
                """.formatted(IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        verify(bankRepository, never()).save(any());
        verify(bankRepository).saveBankCode("37040044");
        verify(ibanRepository).saveIfAbsent(IBAN, 3L);
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

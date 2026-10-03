package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InternalIbanValidatorTest {

    private final InternalIbanValidator validator = new InternalIbanValidator();

    @ParameterizedTest
    @CsvSource({
            "DE89370400440532013000, DE, Germany",
            "GB82WEST12345698765432, GB, United Kingdom",
            "AT611904300234573201, AT, Austria",
            "CH9300762011623852957, CH, Switzerland",
            "NL91ABNA0417164300, NL, Netherlands",
            "NO9386011117947, NO, Norway",
            "FR1420041010050500013M02606, FR, France",
            "MT84MALT011000012345MTLCAST001S, MT, Malta"
    })
    void acceptsValidIbans(String iban, String countryCode, String countryName) {
        IbanValidationResult result = validator.validate(iban);

        assertThat(result.isValid()).isTrue();
        assertThat(result.countryCode()).isEqualTo(countryCode);
        assertThat(result.countryName()).isEqualTo(countryName);
        assertThat(result.failureMessage()).isNull();
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"DE89_3704_0044_0532_0130_00", "1234567890", "DEXX370400440532013000", "DE"})
    void rejectsInvalidCharactersOrStructure(String iban) {
        assertThat(validator.validate(iban).failureMessage()).isEqualTo("Invalid IBAN Structure");
    }

    @Test
    void rejectsUnknownCountry() {
        assertThat(validator.validate("XX89370400440532013000").failureMessage())
                .isEqualTo("This country does not support IBAN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE8937040044053201300", "DE893704004405320130000"})
    void rejectsWrongLength(String iban) {
        assertThat(validator.validate(iban).failureMessage()).isEqualTo("Invalid IBAN Length");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE88370400440532013000", "DE89370400440532013001", "GB82WEST12345698765433"})
    void rejectsWrongChecksum(String iban) {
        assertThat(validator.validate(iban).failureMessage()).isEqualTo("Invalid IBAN Checksum");
    }
}

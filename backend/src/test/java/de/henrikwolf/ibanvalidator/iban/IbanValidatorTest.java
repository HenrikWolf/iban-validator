package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;

import de.henrikwolf.ibanvalidator.iban.IbanValidationResult.FailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanValidatorTest {

    private final IbanValidator validator = new IbanValidator();

    @ParameterizedTest
    @CsvSource({
            "DE89370400440532013000, DE",
            "GB82WEST12345698765432, GB",
            "AT611904300234573201, AT",
            "CH9300762011623852957, CH",
            "NL91ABNA0417164300, NL",
            "NO9386011117947, NO",
            "FR1420041010050500013M02606, FR",
            "MT84MALT011000012345MTLCAST001S, MT"
    })
    void acceptsValidIbans(String iban, String countryCode) {
        IbanValidationResult result = validator.validate(iban);

        assertThat(result.isValid()).isTrue();
        assertThat(result.countryCode()).isEqualTo(countryCode);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void ignoresWhitespaceAndCase() {
        IbanValidationResult result = validator.validate(" de89 3704 0044 0532 0130 00 ");

        assertThat(result.isValid()).isTrue();
        assertThat(result.iban()).isEqualTo("DE89370400440532013000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DE89-3704-0044-0532-0130-00", "1234567890", "DEXX370400440532013000", "DE"})
    void rejectsInvalidCharactersOrStructure(String iban) {
        assertThat(validator.validate(iban).failureReason()).isEqualTo(FailureReason.INVALID_CHARACTERS);
    }

    @Test
    void rejectsUnknownCountry() {
        assertThat(validator.validate("XX89370400440532013000").failureReason())
                .isEqualTo(FailureReason.UNSUPPORTED_COUNTRY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE8937040044053201300", "DE893704004405320130000"})
    void rejectsWrongLength(String iban) {
        assertThat(validator.validate(iban).failureReason()).isEqualTo(FailureReason.INVALID_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE88370400440532013000", "DE89370400440532013001", "GB82WEST12345698765433"})
    void rejectsWrongChecksum(String iban) {
        assertThat(validator.validate(iban).failureReason()).isEqualTo(FailureReason.INVALID_CHECKSUM);
    }
}

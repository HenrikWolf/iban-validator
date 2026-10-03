package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbanNormalizerTest {

    private final IbanNormalizer normalizer = new IbanNormalizer();

    @ParameterizedTest
    @ValueSource(strings = {
            " de89 3704 0044 0532 0130 00 ",
            "DE89-3704-0044-0532-0130-00",
            "DE89.3704.0044.0532.0130.00",
            "DE89\u00A03704\u00A00044\u00A00532\u00A00130\u00A000",
            "DE89\u202F3704\u202F0044\u202F0532\u202F0130\u202F00",
            "DE89 3704-0044.0532\t0130\n00"
    })
    void removesSeparatorsAndUpperCases(String input) {
        assertThat(normalizer.normalize(input)).isEqualTo("DE89370400440532013000");
    }

    @Test
    void keepsOtherCharacters() {
        assertThat(normalizer.normalize("de89_3704")).isEqualTo("DE89_3704");
    }

    @Test
    void turnsNullIntoEmptyString() {
        assertThat(normalizer.normalize(null)).isEmpty();
    }
}

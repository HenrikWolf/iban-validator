package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IbanCryptographyTest {

    private static final String IBAN = "DE89370400440532013000";
    private static final String SALT = "66eeafa80f6c896ab6146bff901cd0a7";

    private final IbanCryptography cryptography = new IbanCryptography("test-key", SALT);

    @Test
    void encryptsAndDecryptsBackToTheOriginal() {
        String encrypted = cryptography.encrypt(IBAN);

        assertThat(encrypted).doesNotContain(IBAN);
        assertThat(cryptography.decrypt(encrypted)).isEqualTo(IBAN);
    }

    @Test
    void producesDifferentCiphertextForEachCall() {
        assertThat(cryptography.encrypt(IBAN)).isNotEqualTo(cryptography.encrypt(IBAN));
    }

    @Test
    void blindIndexIsDeterministicAndDiffersPerIban() {
        assertThat(cryptography.blindIndex(IBAN))
                .isEqualTo(cryptography.blindIndex(IBAN))
                .isNotEqualTo(cryptography.blindIndex("FR7630006000011234567890189"))
                .hasSize(64);
    }

    @Test
    void otherKeyProducesOtherBlindIndexAndCannotDecrypt() {
        IbanCryptography other = new IbanCryptography("other-key", SALT);

        assertThat(other.blindIndex(IBAN)).isNotEqualTo(cryptography.blindIndex(IBAN));
        assertThatThrownBy(() -> other.decrypt(cryptography.encrypt(IBAN)))
                .isInstanceOf(IllegalStateException.class);
    }
}
package de.henrikwolf.ibanvalidator.iban;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts an IBAN for storage (reversible) and computes a deterministic blind index, so duplicates can be
 * detected without keeping the IBAN in clear text.
 */
@Component
public class IbanCryptography {

    private static final String HMAC = "HmacSHA256";

    private final TextEncryptor encryptor;
    private final SecretKeySpec indexKey;

    public IbanCryptography(@Value("${iban.encryption-key}") String key,
                            @Value("${iban.encryption-salt}") String salt) {
        // AES-256-GCM with a random IV per call; the AES key is derived from key and salt via PBKDF2.
        this.encryptor = Encryptors.delux(key, salt);
        this.indexKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    public String encrypt(String iban) {
        return encryptor.encrypt(iban);
    }

    public String decrypt(String encrypted) {
        return encryptor.decrypt(encrypted);
    }

    /** Deterministic keyed hash (HMAC-SHA256, hex) of the IBAN, used as unique key to detect duplicates. */
    public String blindIndex(String iban) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(indexKey);
            return HexFormat.of().formatHex(mac.doFinal(iban.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute blind index", e);
        }
    }
}
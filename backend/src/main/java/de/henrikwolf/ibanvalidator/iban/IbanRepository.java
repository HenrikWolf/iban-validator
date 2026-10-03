package de.henrikwolf.ibanvalidator.iban;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IbanRepository {

    private final JdbcClient jdbcClient;
    private final IbanCryptography cryptography;

    public IbanRepository(JdbcClient jdbcClient, IbanCryptography cryptography) {
        this.jdbcClient = jdbcClient;
        this.cryptography = cryptography;
    }

    /** Stores the IBAN encrypted unless an equal one is already present; the first timestamp is kept. */
    public void saveIfAbsent(String iban) {
        jdbcClient.sql("INSERT INTO iban (iban_lookup, iban_encrypted) VALUES (:lookup, :encrypted) "
                        + "ON CONFLICT (iban_lookup) DO NOTHING")
                .param("lookup", cryptography.blindIndex(iban))
                .param("encrypted", cryptography.encrypt(iban))
                .update();
    }

    /** Deletes stored IBANs first seen before the given cut-off and returns how many were removed. */
    public int deleteOlderThan(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM iban WHERE created_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }
}

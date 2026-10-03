package de.henrikwolf.ibanvalidator.iban;

import java.time.Instant;
import java.util.Optional;
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

    /**
     * Stores the IBAN encrypted unless an equal one is already present; the first timestamp is kept.
     * A given bank replaces the linked one; without a bank an existing link is kept.
     */
    public void saveIfAbsent(String iban, Long bankId) {
        jdbcClient.sql("INSERT INTO iban (iban_lookup, iban_encrypted, bank_id) VALUES (:lookup, :encrypted, :bankId) "
                        + "ON CONFLICT (iban_lookup) DO UPDATE SET bank_id = COALESCE(EXCLUDED.bank_id, iban.bank_id)")
                .param("lookup", cryptography.blindIndex(iban))
                .param("encrypted", cryptography.encrypt(iban))
                .param("bankId", bankId)
                .update();
    }

    /** Returns the bank linked to the stored IBAN, if the IBAN is stored and has a bank. */
    public Optional<Bank> findBank(String iban) {
        return jdbcClient.sql("SELECT b.bic, b.bank_name, b.country_name, b.bank_code FROM iban i "
                        + "JOIN bank b ON b.id = i.bank_id WHERE i.iban_lookup = :lookup")
                .param("lookup", cryptography.blindIndex(iban))
                .query((rs, n) -> new Bank(rs.getString("bic"), rs.getString("bank_name"),
                        rs.getString("country_name"), rs.getString("bank_code")))
                .optional();
    }

    /** Deletes stored IBANs first seen before the given cut-off and returns how many were removed. */
    public int deleteOlderThan(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM iban WHERE created_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }
}
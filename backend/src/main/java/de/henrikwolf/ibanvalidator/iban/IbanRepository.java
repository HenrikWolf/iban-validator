package de.henrikwolf.ibanvalidator.iban;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IbanRepository {

    private final JdbcClient jdbcClient;

    public IbanRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Stores the IBAN unless it is already present; the first timestamp is kept. */
    public void saveIfAbsent(String iban) {
        jdbcClient.sql("INSERT INTO iban (iban) VALUES (:iban) ON CONFLICT (iban) DO NOTHING")
                .param("iban", iban)
                .update();
    }
}

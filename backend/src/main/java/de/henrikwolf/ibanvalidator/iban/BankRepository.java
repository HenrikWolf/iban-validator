package de.henrikwolf.ibanvalidator.iban;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BankRepository {

    private final JdbcClient jdbcClient;

    public BankRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Stores bank data reported by a validator and returns its id. A German bank is keyed by its bank code,
     * a foreign one by its BIC; an existing entry is overwritten with the newer values.
     */
    public long save(Bank bank) {
        return bank.bankCode() != null ? saveGermanBank(bank) : saveForeignBank(bank);
    }

    /** Makes sure the German bank code is known (with its country) and returns the id of its entry. */
    public long saveBankCode(String bankCode, String countryName) {
        return jdbcClient.sql("INSERT INTO bank (bank_code, country_name) VALUES (:bankCode, :countryName) "
                        + "ON CONFLICT (bank_code) WHERE bank_code IS NOT NULL "
                        + "DO UPDATE SET country_name = COALESCE(bank.country_name, EXCLUDED.country_name) RETURNING id")
                .param("bankCode", bankCode)
                .param("countryName", countryName)
                .query(Long.class)
                .single();
    }

    private long saveGermanBank(Bank bank) {
        return jdbcClient.sql("INSERT INTO bank (bic, bank_name, country_name, bank_code) "
                        + "VALUES (:bic, :bankName, :countryName, :bankCode) "
                        + "ON CONFLICT (bank_code) WHERE bank_code IS NOT NULL "
                        + "DO UPDATE SET bic = EXCLUDED.bic, bank_name = EXCLUDED.bank_name, "
                        + "country_name = COALESCE(EXCLUDED.country_name, bank.country_name), updated_at = now() "
                        + "RETURNING id")
                .param("bic", bank.bic())
                .param("bankName", bank.bankName())
                .param("countryName", bank.countryName())
                .param("bankCode", bank.bankCode())
                .query(Long.class)
                .single();
    }

    private long saveForeignBank(Bank bank) {
        return jdbcClient.sql("INSERT INTO bank (bic, bank_name, country_name) "
                        + "VALUES (:bic, :bankName, :countryName) "
                        + "ON CONFLICT (bic) WHERE bank_code IS NULL "
                        + "DO UPDATE SET bank_name = EXCLUDED.bank_name, "
                        + "country_name = COALESCE(EXCLUDED.country_name, bank.country_name), updated_at = now() "
                        + "RETURNING id")
                .param("bic", bank.bic())
                .param("bankName", bank.bankName())
                .param("countryName", bank.countryName())
                .query(Long.class)
                .single();
    }
}
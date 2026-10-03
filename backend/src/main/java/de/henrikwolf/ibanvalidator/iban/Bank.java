package de.henrikwolf.ibanvalidator.iban;

/**
 * The bank code is the national one (only filled for Germany: the Bankleitzahl).
 * Entries created from a bank code alone have no BIC and no bank name yet.
 */
public record Bank(String bic, String bankName, String countryName, String bankCode) {
}
package de.henrikwolf.ibanvalidator.iban;

public record IbanValidationResult(String iban, String countryCode, String failureMessage) {

    static IbanValidationResult valid(String iban, String countryCode) {
        return new IbanValidationResult(iban, countryCode, null);
    }

    static IbanValidationResult invalid(String iban, String message) {
        return new IbanValidationResult(iban, null, message);
    }

    public boolean isValid() {
        return failureMessage == null;
    }
}
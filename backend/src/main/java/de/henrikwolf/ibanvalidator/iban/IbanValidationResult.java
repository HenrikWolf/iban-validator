package de.henrikwolf.ibanvalidator.iban;

public record IbanValidationResult(String iban, String countryCode, FailureReason failureReason) {

    public enum FailureReason {
        INVALID_CHARACTERS,
        UNSUPPORTED_COUNTRY,
        INVALID_LENGTH,
        INVALID_CHECKSUM
    }

    static IbanValidationResult valid(String iban, String countryCode) {
        return new IbanValidationResult(iban, countryCode, null);
    }

    static IbanValidationResult invalid(String iban, FailureReason reason) {
        return new IbanValidationResult(iban, null, reason);
    }

    public boolean isValid() {
        return failureReason == null;
    }
}

package de.henrikwolf.ibanvalidator.iban;

import java.util.Locale;

public record IbanValidationResult(String iban, String countryCode, String countryName, String failureMessage) {

    static IbanValidationResult valid(String iban, String countryCode) {
        return valid(iban, countryCode, null);
    }

    /** Without a given name, the English country name is derived from the country code. */
    static IbanValidationResult valid(String iban, String countryCode, String countryName) {
        return new IbanValidationResult(iban, countryCode,
                countryName != null ? countryName : countryNameOf(countryCode), null);
    }

    static IbanValidationResult invalid(String iban, String message) {
        return new IbanValidationResult(iban, null, null, message);
    }

    public boolean isValid() {
        return failureMessage == null;
    }

    private static String countryNameOf(String countryCode) {
        String name = Locale.of("", countryCode).getDisplayCountry(Locale.ENGLISH);
        // Unknown codes are returned unchanged; then there is no name.
        return name.isEmpty() || name.equals(countryCode) ? null : name;
    }
}
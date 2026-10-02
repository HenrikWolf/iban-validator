package de.henrikwolf.ibanvalidator.iban;

import de.henrikwolf.ibanvalidator.iban.IbanValidationResult.FailureReason;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IbanValidator {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern IBAN_STRUCTURE = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]+");

    public IbanValidationResult validate(String rawIban) {
        String iban = normalize(rawIban);

        if (!IBAN_STRUCTURE.matcher(iban).matches()) {
            return IbanValidationResult.invalid(iban, FailureReason.INVALID_CHARACTERS);
        }

        String countryCode = iban.substring(0, 2);
        Integer expectedLength = IbanCountryLengths.lengthOf(countryCode);
        if (expectedLength == null) {
            return IbanValidationResult.invalid(iban, FailureReason.UNSUPPORTED_COUNTRY);
        }
        if (iban.length() != expectedLength) {
            return IbanValidationResult.invalid(iban, FailureReason.INVALID_LENGTH);
        }
        if (mod97(iban) != 1) {
            return IbanValidationResult.invalid(iban, FailureReason.INVALID_CHECKSUM);
        }
        return IbanValidationResult.valid(iban, countryCode);
    }

    private static String normalize(String rawIban) {
        return rawIban == null ? "" : WHITESPACE.matcher(rawIban).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /**
     * ISO 7064 MOD 97-10: move the first four characters to the end, replace letters with
     * two-digit numbers (A=10 ... Z=35) and compute the remainder piecewise.
     */
    private static int mod97(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            int value = Character.digit(rearranged.charAt(i), 36);
            remainder = value > 9
                    ? (remainder * 100 + value) % 97
                    : (remainder * 10 + value) % 97;
        }
        return remainder;
    }
}

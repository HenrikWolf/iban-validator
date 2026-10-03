package de.henrikwolf.ibanvalidator.iban;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Validates IBANs locally: structure, country specific length and mod-97 check digits. */
@Service
public class InternalIbanValidator implements IbanValidator {

    private static final Pattern IBAN_STRUCTURE = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]+");

    @Override
    public IbanValidationResult validate(String iban) {
        if (!IBAN_STRUCTURE.matcher(iban).matches()) {
            return IbanValidationResult.invalid(iban, "Invalid IBAN Structure");
        }

        String countryCode = iban.substring(0, 2);
        Integer expectedLength = IbanCountryLengths.lengthOf(countryCode);
        if (expectedLength == null) {
            return IbanValidationResult.invalid(iban, "This country does not support IBAN");
        }
        if (iban.length() != expectedLength) {
            return IbanValidationResult.invalid(iban, "Invalid IBAN Length");
        }
        if (mod97(iban) != 1) {
            return IbanValidationResult.invalid(iban, "Invalid IBAN Checksum");
        }
        return IbanValidationResult.valid(iban, countryCode);
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

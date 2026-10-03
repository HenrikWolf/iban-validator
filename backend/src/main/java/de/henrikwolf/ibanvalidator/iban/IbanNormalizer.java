package de.henrikwolf.ibanvalidator.iban;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes separators (whitespace incl. non-breaking spaces, hyphens, dots) and upper-cases the input. */
@Component
public class IbanNormalizer {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\u00A0\\u202F.\\-]+");

    public String normalize(String rawIban) {
        return rawIban == null ? "" : SEPARATORS.matcher(rawIban).replaceAll("").toUpperCase(Locale.ROOT);
    }
}

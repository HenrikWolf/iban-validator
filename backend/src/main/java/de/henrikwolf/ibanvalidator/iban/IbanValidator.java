package de.henrikwolf.ibanvalidator.iban;

/** Validates an already normalized IBAN (no separators, upper case). */
public interface IbanValidator {

    /**
     * @throws ValidatorUnavailableException if the validator cannot give an answer right now
     */
    IbanValidationResult validate(String iban);
}

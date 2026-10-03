package de.henrikwolf.ibanvalidator.iban;

/** A validator cannot give an answer right now (e.g. the external service is down or rejects our API key). */
public class ValidatorUnavailableException extends RuntimeException {

    public ValidatorUnavailableException(String message) {
        super(message);
    }

    public ValidatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

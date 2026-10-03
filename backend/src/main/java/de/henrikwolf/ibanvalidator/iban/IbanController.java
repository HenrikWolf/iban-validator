package de.henrikwolf.ibanvalidator.iban;

import de.henrikwolf.ibanvalidator.api.IbanApi;
import de.henrikwolf.ibanvalidator.api.model.IbanFailureReason;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationRequest;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IbanController implements IbanApi {

    private static final Logger log = LoggerFactory.getLogger(IbanController.class);

    private final IbanValidator ibanValidator;
    private final IbanRepository ibanRepository;

    public IbanController(IbanValidator ibanValidator, IbanRepository ibanRepository) {
        this.ibanValidator = ibanValidator;
        this.ibanRepository = ibanRepository;
    }

    @Override
    public ResponseEntity<IbanValidationResponse> validateIban(IbanValidationRequest request) {
        IbanValidationResult result = ibanValidator.validate(request.getIban());
        if (result.isValid()) {
            store(result.iban());
        }
        IbanValidationResponse response = new IbanValidationResponse(result.iban(), result.isValid())
                .countryCode(result.countryCode())
                .failureReason(result.isValid() ? null : IbanFailureReason.valueOf(result.failureReason().name()));
        return ResponseEntity.ok(response);
    }

    // Storing is secondary: a database problem must not break the validation.
    private void store(String iban) {
        try {
            ibanRepository.saveIfAbsent(iban);
        } catch (DataAccessException e) {
            log.warn("Could not store IBAN", e);
        }
    }
}

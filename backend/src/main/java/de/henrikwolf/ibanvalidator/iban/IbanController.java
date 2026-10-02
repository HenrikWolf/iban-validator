package de.henrikwolf.ibanvalidator.iban;

import de.henrikwolf.ibanvalidator.api.IbanApi;
import de.henrikwolf.ibanvalidator.api.model.IbanFailureReason;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationRequest;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IbanController implements IbanApi {

    private final IbanValidator ibanValidator;

    public IbanController(IbanValidator ibanValidator) {
        this.ibanValidator = ibanValidator;
    }

    @Override
    public ResponseEntity<IbanValidationResponse> validateIban(IbanValidationRequest request) {
        IbanValidationResult result = ibanValidator.validate(request.getIban());
        IbanValidationResponse response = new IbanValidationResponse(result.iban(), result.isValid())
                .countryCode(result.countryCode())
                .failureReason(result.isValid() ? null : IbanFailureReason.valueOf(result.failureReason().name()));
        return ResponseEntity.ok(response);
    }
}

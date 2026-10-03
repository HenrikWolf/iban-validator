package de.henrikwolf.ibanvalidator.iban;

import de.henrikwolf.ibanvalidator.api.IbanApi;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationRequest;
import de.henrikwolf.ibanvalidator.api.model.IbanValidationResponse;
import de.henrikwolf.ibanvalidator.api.model.IbanValidatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IbanController implements IbanApi {

    private static final Logger log = LoggerFactory.getLogger(IbanController.class);

    private final IbanNormalizer ibanNormalizer;
    private final InternalIbanValidator internalIbanValidator;
    private final IbanApiValidator ibanApiValidator;
    private final IbanApiValidator ibanApiExtendedValidator;
    private final IbanRepository ibanRepository;
    private final BankRepository bankRepository;

    public IbanController(IbanNormalizer ibanNormalizer, InternalIbanValidator internalIbanValidator,
                          @Qualifier("ibanApiValidator") IbanApiValidator ibanApiValidator,
                          @Qualifier("ibanApiExtendedValidator") IbanApiValidator ibanApiExtendedValidator,
                          IbanRepository ibanRepository, BankRepository bankRepository) {
        this.ibanNormalizer = ibanNormalizer;
        this.internalIbanValidator = internalIbanValidator;
        this.ibanApiValidator = ibanApiValidator;
        this.ibanApiExtendedValidator = ibanApiExtendedValidator;
        this.ibanRepository = ibanRepository;
        this.bankRepository = bankRepository;
    }

    @Override
    public ResponseEntity<IbanValidationResponse> validateIban(IbanValidationRequest request) {
        String iban = ibanNormalizer.normalize(request.getIban());
        if (iban.isEmpty()) {
            // Free input may contain separators; if nothing is left after normalizing, treat it as missing.
            return ResponseEntity.badRequest().build();
        }
        IbanValidationResult result = validatorFor(request.getValidator()).validate(iban);
        Bank bank = result.isValid() ? storeAndFindBank(result) : null;
        IbanValidationResponse response = new IbanValidationResponse(result.iban(), result.isValid())
                .countryCode(result.countryCode())
                .countryName(result.countryName())
                .bankName(bank != null ? bank.bankName() : null)
                .bic(bank != null ? bank.bic() : null)
                .failureMessage(result.failureMessage());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ValidatorUnavailableException.class)
    ResponseEntity<ProblemDetail> handleValidatorUnavailable(ValidatorUnavailableException e) {
        log.warn("Validator unavailable", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "IBAN validation is currently unavailable, please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    private IbanValidator validatorFor(IbanValidatorType type) {
        return switch (type) {
            case IBANAPI -> ibanApiValidator;
            case IBANAPI_EXTENDED -> ibanApiExtendedValidator;
            case INTERNAL -> internalIbanValidator;
            case null -> internalIbanValidator;
        };
    }

    // Storing and reading banks is secondary: a database problem must not break the validation.
    private Bank storeAndFindBank(IbanValidationResult result) {
        Bank reported = bankOf(result);
        try {
            if (reported != null) {
                ibanRepository.saveIfAbsent(result.iban(), bankRepository.save(reported));
                return reported;
            }
            String bankCode = germanBankCode(result);
            boolean linked = ibanRepository.findBank(result.iban()).isPresent();
            Long bankId = bankCode != null && !linked ? bankRepository.saveBankCode(bankCode) : null;
            ibanRepository.saveIfAbsent(result.iban(), bankId);
            return ibanRepository.findBank(result.iban()).orElse(null);
        } catch (DataAccessException e) {
            log.warn("Could not store IBAN or bank", e);
            return reported;
        }
    }

    private Bank bankOf(IbanValidationResult result) {
        if (result.bic() == null || result.bankName() == null) {
            return null;
        }
        return new Bank(result.bic(), result.bankName(), result.countryName(), germanBankCode(result));
    }

    // German IBANs carry the 8-digit Bankleitzahl at positions 5-12.
    private String germanBankCode(IbanValidationResult result) {
        return "DE".equals(result.countryCode()) && result.iban().length() >= 12
                ? result.iban().substring(4, 12) : null;
    }
}
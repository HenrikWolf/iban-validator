package de.henrikwolf.ibanvalidator.iban;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces storage limitation: stored IBANs are only kept for {@code iban.retention} and then deleted.
 * Runs on the schedule {@code iban.cleanup-cron} (default: daily at 03:00).
 */
@Component
public class IbanRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(IbanRetentionJob.class);

    private final IbanRepository ibanRepository;
    private final Duration retention;

    public IbanRetentionJob(IbanRepository ibanRepository,
                            @Value("${iban.retention}") Duration retention) {
        this.ibanRepository = ibanRepository;
        this.retention = retention;
    }

    @Scheduled(cron = "${iban.cleanup-cron}")
    public void deleteExpired() {
        int deleted = ibanRepository.deleteOlderThan(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} expired IBAN(s) older than {}", deleted, retention);
        }
    }
}

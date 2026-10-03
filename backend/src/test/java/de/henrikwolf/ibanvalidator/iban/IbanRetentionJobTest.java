package de.henrikwolf.ibanvalidator.iban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IbanRetentionJobTest {

    @Mock
    private IbanRepository ibanRepository;

    @Test
    void deletesIbansOlderThanTheRetentionPeriod() {
        Duration retention = Duration.ofDays(30);
        IbanRetentionJob job = new IbanRetentionJob(ibanRepository, retention);
        when(ibanRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any())).thenReturn(3);

        Instant before = Instant.now().minus(retention);
        job.deleteExpired();
        Instant after = Instant.now().minus(retention);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(ibanRepository).deleteOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }
}

package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import dev.niceone.smartsiteappserver.repository.OnsiteCheckLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OnsiteCheckLogServiceTest {

    private OnsiteCheckLogRepository repository;
    private OnsiteCheckLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(OnsiteCheckLogRepository.class);
        service = new OnsiteCheckLogService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "minIntervalSec", 300L);
    }

    @Test
    void repeatedIdenticalAnswersAreStoredOncePerInterval() {
        for (int i = 0; i < 50; i++) {
            service.save("PANDM", "10.0.0.1", 0, "No data in DB for site: PANDM");
        }
        verify(repository, times(1)).save(any(OnsiteCheckLog.class));
    }

    @Test
    void changedAnswerIsStoredImmediately() {
        service.save("PANDM", "10.0.0.1", 0, "No data");
        service.save("PANDM", "10.0.0.1", 1, "On-Site");
        service.save("PANDM", "10.0.0.1", 1, "On-Site");
        verify(repository, times(2)).save(any(OnsiteCheckLog.class));
    }

    @Test
    void differentSitesAndClientsAreTrackedSeparately() {
        service.save("PANDM", "10.0.0.1", 0, "No data");
        service.save("PANDM", "10.0.0.2", 0, "No data");
        service.save("MBRNM", "10.0.0.1", 0, "No data");
        verify(repository, times(3)).save(any(OnsiteCheckLog.class));
    }

    @Test
    void zeroIntervalStoresEveryRequest() {
        ReflectionTestUtils.setField(service, "minIntervalSec", 0L);
        for (int i = 0; i < 5; i++) {
            service.save("PANDM", "10.0.0.1", 0, "No data");
        }
        verify(repository, times(5)).save(any(OnsiteCheckLog.class));
    }

    @Test
    void retentionDisabledByDefaultDeletesNothing() {
        service.purgeOldRows();
        verify(repository, never()).deleteOlderThan(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}

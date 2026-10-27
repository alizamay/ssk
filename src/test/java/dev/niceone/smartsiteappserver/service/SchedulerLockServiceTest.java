package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.repository.SchedulerLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerLockServiceTest {

    private SchedulerLockRepository repository;
    private SchedulerLockService service;

    @BeforeEach
    void setUp() {
        repository = mock(SchedulerLockRepository.class);
        service = new SchedulerLockService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void becomesLeaderWhenUpdateSucceeds() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt())).thenReturn(1);

        assertTrue(service.tryAcquireOrRenew());
        assertTrue(service.isLeader());
    }

    @Test
    void staysStandbyWhenAnotherNodeHoldsTheLock() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt())).thenReturn(0);

        assertFalse(service.tryAcquireOrRenew());
        assertFalse(service.isLeader());
    }

    @Test
    void losesLeadershipWhenRenewIsRejected() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt())).thenReturn(1, 0);

        assertTrue(service.tryAcquireOrRenew());
        assertFalse(service.tryAcquireOrRenew());
        assertFalse(service.isLeader());
    }

    @Test
    void dbErrorKeepsCurrentLeaseUntilItExpires() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt()))
                .thenReturn(1)
                .thenThrow(new RuntimeException("connection not available"));

        assertTrue(service.tryAcquireOrRenew());
        // lease (90s) is still valid right after a failed renew
        assertTrue(service.tryAcquireOrRenew());
    }

    @Test
    void leadershipExpiresWithoutRenew() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt())).thenReturn(1);
        service.tryAcquireOrRenew();

        // simulate a heartbeat that stopped long ago (e.g. a long GC pause)
        ReflectionTestUtils.setField(service, "leaderUntilNanos", System.nanoTime() - 1);

        assertFalse(service.isLeader());
    }

    @Test
    void releaseClearsLeadershipAndExpiresOwnLockOnly() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), anyInt())).thenReturn(1);
        service.tryAcquireOrRenew();

        service.releaseLock();

        assertFalse(service.isLeader());
        verify(repository).release(eq("main_scheduler"), eq(service.getNodeId()));
    }
}

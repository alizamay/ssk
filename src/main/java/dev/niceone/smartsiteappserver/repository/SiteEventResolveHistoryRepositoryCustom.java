package dev.niceone.smartsiteappserver.repository;

import java.time.LocalDate;

public interface SiteEventResolveHistoryRepositoryCustom {
    Long getNextId();
    void ensurePartitionFor(LocalDate date);
}

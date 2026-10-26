package dev.niceone.smartsiteappserver.elastic.repository;

import dev.niceone.smartsiteappserver.model.ActivityLog;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.scheduling.annotation.Async;

public interface ActivityLogRepository extends ElasticsearchRepository<ActivityLog, Long> {
    @Async
    public <S extends ActivityLog> S save(S entity);
}
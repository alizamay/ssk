package dev.niceone.smartsiteappserver.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SiteEventResolveHistoryRepositoryImpl implements SiteEventResolveHistoryRepositoryCustom {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Override
    public Long getNextId() {
        Query query = em.createNativeQuery("SELECT nextval('smartsite.site_event_resolve_history_id_seq')");
        Object result = query.getSingleResult();
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        return null;
    }

    public void ensurePartitionFor(LocalDate date) {
        String partitionName = String.format("site_event_resolve_history_%s",
                date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd")));

        String start = date.atStartOfDay().toString();
        String end = date.plusDays(1).atStartOfDay().toString();

        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS smartsite.%s " +
                        "PARTITION OF smartsite.site_event_resolve_history " +
                        "FOR VALUES FROM ('%s') TO ('%s');",
                partitionName, start, end
        );

        jdbcTemplate.execute(sql);
    }
}

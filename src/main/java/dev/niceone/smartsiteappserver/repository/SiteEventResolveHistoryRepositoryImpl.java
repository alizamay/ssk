package dev.niceone.smartsiteappserver.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    // Called on every resolve event. Run the DDL once per day per node, and never against a
    // table that is not partitioned (a failed DDL aborts the caller's transaction, losing the
    // resolve and its clear message).
    private final Set<LocalDate> ensuredDates = ConcurrentHashMap.newKeySet();
    private volatile Boolean parentPartitioned;

    public void ensurePartitionFor(LocalDate date) {
        if (ensuredDates.contains(date)) {
            return;
        }
        if (parentPartitioned == null) {
            List<String> kind = jdbcTemplate.queryForList(
                    "SELECT c.relkind::text FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE n.nspname = 'smartsite' AND c.relname = 'site_event_resolve_history'", String.class);
            parentPartitioned = !kind.isEmpty() && "p".equals(kind.get(0));
        }
        if (!parentPartitioned) {
            ensuredDates.add(date);
            return;
        }
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
        ensuredDates.add(date);
    }
}

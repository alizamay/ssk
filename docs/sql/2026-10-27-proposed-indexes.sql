-- Proposed indexes for alarm / TTSM queries (2026-10-27). NOT applied anywhere yet.
-- Review with the DBA and run in a maintenance window:
--   * On a partitioned parent, CREATE INDEX locks every partition against writes while it builds.
--     For zero-downtime, build each partition with CREATE INDEX CONCURRENTLY, then
--     CREATE INDEX ... ON ONLY <parent> and ALTER INDEX <parent_idx> ATTACH PARTITION <part_idx>.
--   * Check the result with EXPLAIN (ANALYZE, BUFFERS) on the queries listed below.

-- SiteEventLogRepository.findFirstBySiteIdAndAlarmTypeAndCabinetIdAndCreateDateBetween
-- (called for every alarm event in getOrCreateAlarmLog; only the PK (id, create_date) exists today)
CREATE INDEX IF NOT EXISTS idx_site_event_log_site_type_cab_date
    ON smartsite.site_event_log (site_id, alarm_type, cabinet_id, create_date);

-- SiteEventResolveHistoryRepository.findByLogIdIn (alarm / activity list pages; no log_id index today)
CREATE INDEX IF NOT EXISTS idx_site_event_resolve_history_log_id
    ON smartsite.site_event_resolve_history (log_id);

-- TtsmWebhookService.getLatestJobStatus: findFirstBySiteCodeOrderByReceivedAtDesc
-- (replaces the single-column idx_ttsm_site_code for this query)
CREATE INDEX IF NOT EXISTS idx_ttsm_site_received
    ON smartsite.ttsm_job_status (site_code, received_at DESC);

-- onsite_check_log gets one row per /api/check/onsite request (~3.5M rows/day across nodes)
-- and has no retention. Agree a retention period first, then delete in batches, e.g. 30 days:
-- DELETE FROM smartsite.onsite_check_log
--  WHERE id IN (SELECT id FROM smartsite.onsite_check_log
--                WHERE request_time < now() - interval '30 days' LIMIT 50000);

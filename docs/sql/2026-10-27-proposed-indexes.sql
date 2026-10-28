-- Proposed indexes for alarm / TTSM queries (2026-10-27). NOT applied anywhere yet.
-- Review with the DBA and run in a maintenance window:
--   * On a partitioned parent, CREATE INDEX locks every partition against writes while it builds.
--     For zero-downtime, build each partition with CREATE INDEX CONCURRENTLY, then
--     CREATE INDEX ... ON ONLY <parent> and ALTER INDEX <parent_idx> ATTACH PARTITION <part_idx>.
--   * Check the result with EXPLAIN (ANALYZE, BUFFERS) on the queries listed below.

-- Every status report (all sites, every few seconds) looks cabinets up by code and loads a site
-- with its cabinets by site_id. Neither column is indexed, so each lookup scans all ~68k rows in
-- 61 partitions. Load test with 20,798 sites (2026-10-28): adding both indexes took report
-- processing from ~177 to ~1,250 sites/min on 2 nodes.
CREATE INDEX IF NOT EXISTS idx_cabinet_code
    ON smartsite.cabinet (code);
CREATE INDEX IF NOT EXISTS idx_cabinet_site_id
    ON smartsite.cabinet (site_id);

-- Loading or saving a cabinet entity joins aux_device on cabinet_id (cascade on Cabinet.aux_devices);
-- only the primary key exists, so every cabinet save scanned all ~90k aux_device rows.
-- Report processing no longer saves the entity, but admin pages and other jobs still do.
CREATE INDEX IF NOT EXISTS idx_aux_device_cabinet_id
    ON smartsite.aux_device (cabinet_id);

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

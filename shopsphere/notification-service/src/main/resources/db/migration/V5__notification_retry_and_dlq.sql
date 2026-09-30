-- V5 (US40): retry bookkeeping + DLQ.
-- delivery_status gains RETRYING and DEAD_LETTERED (column has no CHECK constraint).

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS attempts         INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at  TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_attempt_at  TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS template_model   TEXT;

-- Rows delivered before US40 were attempted exactly once.
UPDATE notifications
SET attempts = 1
WHERE attempts = 0
  AND delivery_status IN ('SENT', 'FAILED', 'SIMULATED', 'SKIPPED');

-- The retry poller's query: WHERE delivery_status = 'RETRYING' AND next_attempt_at <= now
CREATE INDEX IF NOT EXISTS idx_notifications_retry_due ON notifications (delivery_status, next_attempt_at);

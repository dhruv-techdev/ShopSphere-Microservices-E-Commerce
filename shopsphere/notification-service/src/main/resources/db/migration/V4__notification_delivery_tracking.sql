-- V4 (US39): record how and where each notification was actually delivered.
-- delivery_status (existing VARCHAR(20)) now holds PENDING / SENT / FAILED / SIMULATED / SKIPPED.

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS channel             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS recipient           VARCHAR(254),
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS failure_reason      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sent_at             TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_notifications_delivery_status ON notifications (delivery_status);

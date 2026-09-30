-- V3 (US36): shipment tracking on orders + idempotency ledger for shipment events.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipment_id     BIGINT,
    ADD COLUMN IF NOT EXISTS carrier         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipped_at      TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivered_at    TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_orders_tracking_number ON orders (tracking_number);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     VARCHAR(100)                NOT NULL PRIMARY KEY,
    event_type   VARCHAR(100)                NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

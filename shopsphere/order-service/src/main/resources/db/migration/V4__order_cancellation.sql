-- V4 (US38): record why and when an order was cancelled.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(40),
    ADD COLUMN IF NOT EXISTS cancelled_at        TIMESTAMP(6) WITH TIME ZONE;

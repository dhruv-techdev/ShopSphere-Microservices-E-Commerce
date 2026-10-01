-- V5 (US45): payment outcome on orders, filled from payment.successful / payment.failed.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_reference      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS paid_at                TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS payment_failed_at      TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS payment_failure_reason VARCHAR(255);

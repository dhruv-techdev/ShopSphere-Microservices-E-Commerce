-- V2 (US36): allow SHIPMENT_DISPATCHED / SHIPMENT_DELIVERED.
-- Hibernate's inline enum CHECK is auto-named notifications_type_check by Postgres,
-- and ddl-auto=update never widens it — hence this migration.

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'ORDER_PLACED',
    'PAYMENT_SUCCESSFUL',
    'PAYMENT_FAILED',
    'LOW_STOCK_ALERT',
    'SHIPMENT_DISPATCHED',
    'SHIPMENT_DELIVERED'
));

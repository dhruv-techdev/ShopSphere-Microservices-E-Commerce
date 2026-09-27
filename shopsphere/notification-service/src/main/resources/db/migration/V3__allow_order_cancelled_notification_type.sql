-- V3 (US38): allow ORDER_CANCELLED.

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'ORDER_PLACED',
    'ORDER_CANCELLED',
    'PAYMENT_SUCCESSFUL',
    'PAYMENT_FAILED',
    'LOW_STOCK_ALERT',
    'SHIPMENT_DISPATCHED',
    'SHIPMENT_DELIVERED'
));

-- V2 (US33): shipping address snapshot on orders.
-- Columns are nullable on purpose: orders placed before US33 have no address.
-- New orders are guaranteed an address by request validation + JPA pre-persist validation.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_phone          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_line1          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS shipping_line2          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS shipping_city           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_state          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_postal_code    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_country        VARCHAR(2);

COMMENT ON COLUMN orders.shipping_country IS 'ISO 3166-1 alpha-2, upper case';

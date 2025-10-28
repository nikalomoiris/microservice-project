-- Add product_id to inventory table and unique constraint
ALTER TABLE IF EXISTS inventory
    ADD COLUMN IF NOT EXISTS product_id bigint;

-- add unique index to ensure one inventory row per product
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_inventory_product_id'
    ) THEN
        CREATE UNIQUE INDEX idx_inventory_product_id ON inventory (product_id);
    END IF;
END$$;

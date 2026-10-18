ALTER TABLE menu_entry
    ADD COLUMN target_portions INT DEFAULT NULL AFTER meal,
    ADD COLUMN cooking_note VARCHAR(500) NOT NULL DEFAULT '' AFTER target_portions,
    ADD COLUMN published_at DATETIME DEFAULT NULL AFTER created_at,
    ADD COLUMN published_by BIGINT DEFAULT NULL AFTER published_at,
    ADD COLUMN prepared_at DATETIME DEFAULT NULL AFTER published_by,
    ADD COLUMN prepared_by BIGINT DEFAULT NULL AFTER prepared_at;

-- Existing menus were already visible to kitchen staff before publication was introduced.
UPDATE menu_entry SET published_at = created_at, published_by = created_by WHERE status <> 'CANCELLED';

CREATE INDEX idx_menu_daily ON menu_entry(canteen_id, service_date, published_at, status);

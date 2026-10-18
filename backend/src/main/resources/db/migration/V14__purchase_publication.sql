ALTER TABLE purchase_plan
    MODIFY COLUMN supplier_id BIGINT DEFAULT NULL,
    ADD COLUMN published_at DATETIME DEFAULT NULL,
    ADD COLUMN published_by BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_plan_publisher FOREIGN KEY (published_by) REFERENCES app_user(id);

-- Existing plans were immediately available for purchasing before publication was introduced.
UPDATE purchase_plan
SET published_at = created_at, published_by = created_by
WHERE status IN ('OPEN', 'CLOSED');

ALTER TABLE purchase_batch
    ADD COLUMN legacy_unplanned BOOLEAN NOT NULL DEFAULT FALSE;

-- Only preexisting unplanned arrivals may finish acceptance without a published demand.
UPDATE purchase_batch SET legacy_unplanned = TRUE WHERE plan_id IS NULL;

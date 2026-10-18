CREATE TABLE purchase_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    planned_on DATE NOT NULL,
    planned_quantity DECIMAL(12,3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_canteen_date (canteen_id, planned_on),
    CONSTRAINT ck_plan_quantity CHECK (planned_quantity > 0),
    CONSTRAINT fk_plan_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_plan_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id),
    CONSTRAINT fk_plan_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_plan_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

ALTER TABLE purchase_batch
    ADD COLUMN plan_id BIGINT DEFAULT NULL AFTER supplier_id,
    ADD CONSTRAINT fk_batch_plan FOREIGN KEY (plan_id) REFERENCES purchase_plan(id);

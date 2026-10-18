CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    operation_date DATE NOT NULL,
    meal VARCHAR(20) NOT NULL,
    details_json JSON NOT NULL,
    recorded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_canteen_date (canteen_id, operation_date),
    CONSTRAINT fk_operation_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_operation_recorder FOREIGN KEY (recorded_by) REFERENCES app_user(id)
);

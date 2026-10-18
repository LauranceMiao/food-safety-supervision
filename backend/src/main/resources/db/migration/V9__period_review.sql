CREATE TABLE period_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    canteen_id BIGINT DEFAULT NULL,
    summary_json JSON NOT NULL,
    conclusion TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    confirmed_by BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME DEFAULT NULL,
    INDEX idx_review_period (period_type,period_start),
    CONSTRAINT fk_review_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_review_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_review_confirmer FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
);

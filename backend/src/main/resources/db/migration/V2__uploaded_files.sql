CREATE TABLE uploaded_file (
    id CHAR(36) PRIMARY KEY,
    purpose VARCHAR(40) NOT NULL,
    canteen_id BIGINT DEFAULT NULL,
    owner_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_owner (owner_id, created_at),
    CONSTRAINT fk_file_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);

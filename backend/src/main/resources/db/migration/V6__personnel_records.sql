CREATE TABLE staff_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    canteen_id BIGINT NOT NULL,
    position_name VARCHAR(80) NOT NULL,
    health_cert_no VARCHAR(100) NOT NULL DEFAULT '',
    health_expires_on DATE DEFAULT NULL,
    certificate_path VARCHAR(500) DEFAULT NULL,
    last_training_on DATE DEFAULT NULL,
    training_note VARCHAR(1000) NOT NULL DEFAULT '',
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_staff_profile UNIQUE (user_id, canteen_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_profile_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_profile_updater FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

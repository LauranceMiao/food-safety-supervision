CREATE TABLE reminder_read (
    user_id BIGINT NOT NULL,
    event_key VARCHAR(150) NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, event_key),
    CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

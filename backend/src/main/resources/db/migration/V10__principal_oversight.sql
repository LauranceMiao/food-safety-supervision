CREATE TABLE issue_oversight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    principal_id BIGINT NOT NULL,
    note TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    close_note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME DEFAULT NULL,
    INDEX idx_oversight_issue (issue_id, created_at),
    CONSTRAINT fk_oversight_issue FOREIGN KEY (issue_id) REFERENCES issue(id),
    CONSTRAINT fk_oversight_principal FOREIGN KEY (principal_id) REFERENCES app_user(id)
);

ALTER TABLE purchase_batch ADD COLUMN specification VARCHAR(120) NOT NULL DEFAULT '' AFTER ingredient_id;

ALTER TABLE menu_entry ADD COLUMN cancel_reason VARCHAR(500) DEFAULT NULL AFTER status;

CREATE TABLE issue_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    note TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_issue_event_issue (issue_id, id),
    CONSTRAINT fk_issue_event_issue FOREIGN KEY (issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_event_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
);

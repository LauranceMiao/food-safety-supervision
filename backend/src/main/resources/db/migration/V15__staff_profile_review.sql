CREATE TABLE staff_profile_submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    canteen_id BIGINT NOT NULL,
    real_name VARCHAR(80) NOT NULL DEFAULT '',
    contact_phone VARCHAR(30) NOT NULL DEFAULT '',
    gender VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    position_name VARCHAR(80) NOT NULL DEFAULT '',
    health_cert_no VARCHAR(100) NOT NULL DEFAULT '',
    health_expires_on DATE DEFAULT NULL,
    certificate_path VARCHAR(500) DEFAULT NULL,
    last_training_on DATE DEFAULT NULL,
    training_note VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    submitted_at DATETIME DEFAULT NULL,
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at DATETIME DEFAULT NULL,
    review_note VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_submission_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_profile_submission_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_profile_submission_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
    CONSTRAINT ck_profile_submission_status CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_profile_submission_gender CHECK (gender IN ('UNSPECIFIED','MALE','FEMALE')),
    INDEX idx_profile_submission_person (user_id,canteen_id,id),
    INDEX idx_profile_submission_review (canteen_id,status,id)
);

ALTER TABLE staff_profile
    ADD COLUMN real_name VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN contact_phone VARCHAR(30) NOT NULL DEFAULT '',
    ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN approved_submission_id BIGINT DEFAULT NULL,
    ADD COLUMN approved_by BIGINT DEFAULT NULL,
    ADD COLUMN approved_at DATETIME DEFAULT NULL,
    ADD CONSTRAINT fk_profile_approved_submission FOREIGN KEY (approved_submission_id) REFERENCES staff_profile_submission(id),
    ADD CONSTRAINT fk_profile_approved_reviewer FOREIGN KEY (approved_by) REFERENCES app_user(id);

-- Existing data remains available to its owner as an unsubmitted draft.
-- A former manager-maintained record does not imply employee submission or manager approval.
INSERT INTO staff_profile_submission(user_id,canteen_id,real_name,position_name,health_cert_no,
    health_expires_on,certificate_path,last_training_on,training_note,status)
SELECT p.user_id,p.canteen_id,LEFT(u.display_name,80),p.position_name,p.health_cert_no,
    p.health_expires_on,p.certificate_path,p.last_training_on,p.training_note,'DRAFT'
FROM staff_profile p JOIN app_user u ON u.id=p.user_id
WHERE EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=p.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'));

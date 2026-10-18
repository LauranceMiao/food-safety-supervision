CREATE TABLE canteen (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    location VARCHAR(200) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stall (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_stall_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT uq_stall_name UNIQUE (canteen_id, name)
);

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    identity_type VARCHAR(20) DEFAULT NULL,
    self_registered BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_name VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE user_canteen (
    user_id BIGINT NOT NULL,
    canteen_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, canteen_id),
    CONSTRAINT fk_scope_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_scope_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id)
);

CREATE TABLE supplier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    license_no VARCHAR(100) NOT NULL DEFAULT '',
    license_expires_on DATE DEFAULT NULL,
    contact_name VARCHAR(100) NOT NULL DEFAULT '',
    contact_phone VARCHAR(40) NOT NULL DEFAULT '',
    certificate_path VARCHAR(500) DEFAULT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_supplier_expiry (license_expires_on),
    CONSTRAINT fk_supplier_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE ingredient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(80) NOT NULL DEFAULT '',
    unit VARCHAR(30) NOT NULL
);

CREATE TABLE purchase_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_code VARCHAR(80) NOT NULL UNIQUE,
    canteen_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    production_date DATE DEFAULT NULL,
    expires_on DATE DEFAULT NULL,
    received_on DATE NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    available_quantity DECIMAL(12,3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    voucher_path VARCHAR(500) DEFAULT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_batch_canteen_date (canteen_id, received_on),
    INDEX idx_batch_supplier (supplier_id),
    INDEX idx_batch_expiry (expires_on),
    CONSTRAINT fk_batch_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_batch_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id),
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_batch_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_batch_quantity CHECK (quantity > 0 AND available_quantity >= 0 AND available_quantity <= quantity)
);

CREATE TABLE acceptance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    note TEXT,
    evidence_path VARCHAR(500) DEFAULT NULL,
    checked_by BIGINT NOT NULL,
    checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_acceptance_batch (batch_id, checked_at),
    CONSTRAINT fk_acceptance_batch FOREIGN KEY (batch_id) REFERENCES purchase_batch(id),
    CONSTRAINT fk_acceptance_checker FOREIGN KEY (checked_by) REFERENCES app_user(id)
);

CREATE TABLE dish (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_dish_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT uq_dish_name UNIQUE (canteen_id, name)
);

CREATE TABLE dish_ingredient (
    dish_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    PRIMARY KEY (dish_id, ingredient_id),
    CONSTRAINT fk_recipe_dish FOREIGN KEY (dish_id) REFERENCES dish(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id)
);

CREATE TABLE menu_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    stall_id BIGINT DEFAULT NULL,
    dish_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    meal VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_menu_service (canteen_id, service_date, meal),
    CONSTRAINT fk_menu_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_menu_stall FOREIGN KEY (stall_id) REFERENCES stall(id),
    CONSTRAINT fk_menu_dish FOREIGN KEY (dish_id) REFERENCES dish(id),
    CONSTRAINT fk_menu_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE batch_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    menu_entry_id BIGINT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_usage_batch (batch_id),
    INDEX idx_usage_menu (menu_entry_id),
    CONSTRAINT fk_usage_batch FOREIGN KEY (batch_id) REFERENCES purchase_batch(id),
    CONSTRAINT fk_usage_menu FOREIGN KEY (menu_entry_id) REFERENCES menu_entry(id),
    CONSTRAINT fk_usage_recorder FOREIGN KEY (recorded_by) REFERENCES app_user(id),
    CONSTRAINT ck_usage_quantity CHECK (quantity > 0)
);

CREATE TABLE check_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE check_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_check_item_template FOREIGN KEY (template_id) REFERENCES check_template(id)
);

CREATE TABLE daily_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    check_date DATE NOT NULL,
    checked_by BIGINT NOT NULL,
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_daily_canteen_date (canteen_id, check_date),
    CONSTRAINT fk_daily_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_daily_template FOREIGN KEY (template_id) REFERENCES check_template(id),
    CONSTRAINT fk_daily_checker FOREIGN KEY (checked_by) REFERENCES app_user(id)
);

CREATE TABLE daily_check_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    daily_check_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_title VARCHAR(200) NOT NULL,
    result VARCHAR(20) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uq_daily_item UNIQUE (daily_check_id, item_id),
    CONSTRAINT fk_result_daily FOREIGN KEY (daily_check_id) REFERENCES daily_check(id) ON DELETE CASCADE,
    CONSTRAINT fk_result_item FOREIGN KEY (item_id) REFERENCES check_item(id)
);

CREATE TABLE inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    inspector_id BIGINT NOT NULL,
    scheduled_at DATETIME NOT NULL,
    started_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    note TEXT,
    cancel_reason VARCHAR(500) DEFAULT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_inspection_schedule (canteen_id, scheduled_at),
    CONSTRAINT fk_inspection_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_inspection_inspector FOREIGN KEY (inspector_id) REFERENCES app_user(id),
    CONSTRAINT fk_inspection_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE issue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT DEFAULT NULL,
    category VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to BIGINT NOT NULL,
    due_at DATETIME NOT NULL,
    needs_report BOOLEAN NOT NULL DEFAULT FALSE,
    report_note TEXT,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME DEFAULT NULL,
    INDEX idx_issue_canteen_status (canteen_id, status),
    INDEX idx_issue_due (due_at),
    CONSTRAINT fk_issue_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_issue_assignee FOREIGN KEY (assigned_to) REFERENCES app_user(id),
    CONSTRAINT fk_issue_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE rectification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    submitter_id BIGINT NOT NULL,
    details TEXT NOT NULL,
    evidence_path VARCHAR(500) DEFAULT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_result VARCHAR(20) DEFAULT NULL,
    reviewed_by BIGINT DEFAULT NULL,
    review_note TEXT,
    reviewed_at DATETIME DEFAULT NULL,
    INDEX idx_rectification_issue (issue_id, submitted_at),
    CONSTRAINT fk_rect_issue FOREIGN KEY (issue_id) REFERENCES issue(id),
    CONSTRAINT fk_rect_submitter FOREIGN KEY (submitter_id) REFERENCES app_user(id),
    CONSTRAINT fk_rect_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id)
);

CREATE TABLE feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    canteen_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    category VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    image_path VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    assigned_to BIGINT DEFAULT NULL,
    investigation_note TEXT,
    investigated_by BIGINT DEFAULT NULL,
    investigated_at DATETIME DEFAULT NULL,
    response TEXT,
    issue_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at DATETIME DEFAULT NULL,
    INDEX idx_feedback_user (user_id, created_at),
    INDEX idx_feedback_status (status),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_feedback_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_feedback_assignee FOREIGN KEY (assigned_to) REFERENCES app_user(id),
    CONSTRAINT fk_feedback_investigator FOREIGN KEY (investigated_by) REFERENCES app_user(id),
    CONSTRAINT fk_feedback_issue FOREIGN KEY (issue_id) REFERENCES issue(id)
);

CREATE TABLE risk_config (
    version INT PRIMARY KEY,
    weights_json JSON NOT NULL,
    thresholds_json JSON NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_config_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE risk_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    canteen_id BIGINT NOT NULL,
    config_version INT NOT NULL,
    score DECIMAL(5,2) DEFAULT NULL,
    level VARCHAR(30) NOT NULL,
    breakdown_json JSON NOT NULL,
    window_start DATE NOT NULL,
    window_end DATE NOT NULL,
    calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_risk_canteen_date (canteen_id, calculated_at),
    CONSTRAINT fk_risk_score_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_risk_score_config FOREIGN KEY (config_version) REFERENCES risk_config(version)
);

CREATE TABLE inspector_availability (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inspector_id BIGINT NOT NULL,
    available_date DATE NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    max_tasks INT NOT NULL DEFAULT 2,
    CONSTRAINT uq_availability UNIQUE (inspector_id, available_date),
    CONSTRAINT fk_availability_inspector FOREIGN KEY (inspector_id) REFERENCES app_user(id)
);

CREATE TABLE schedule_suggestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_date DATE NOT NULL,
    canteen_id BIGINT NOT NULL,
    inspector_id BIGINT NOT NULL,
    priority_score DECIMAL(8,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUGGESTED',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME DEFAULT NULL,
    inspection_id BIGINT DEFAULT NULL,
    INDEX idx_schedule_date (plan_date, status),
    CONSTRAINT fk_schedule_canteen FOREIGN KEY (canteen_id) REFERENCES canteen(id),
    CONSTRAINT fk_schedule_inspector FOREIGN KEY (inspector_id) REFERENCES app_user(id),
    CONSTRAINT fk_schedule_inspection FOREIGN KEY (inspection_id) REFERENCES inspection(id)
);

CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT DEFAULT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT DEFAULT NULL,
    detail TEXT,
    ip_address VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_actor (actor_id, created_at),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
);

INSERT INTO canteen (name, location) VALUES
('第一食堂', '东校区'), ('第二食堂', '西校区'), ('第三食堂', '南校区');

INSERT INTO check_template (name) VALUES ('食堂每日食品安全检查');
INSERT INTO check_item (template_id, title, sort_order) VALUES
(1, '从业人员健康与着装', 10),
(1, '原料贮存与标识', 20),
(1, '加工区域卫生', 30),
(1, '餐饮具清洗消毒', 40),
(1, '留样与记录', 50),
(1, '有害生物防制', 60);

ALTER TABLE inspection
    ADD COLUMN template_id BIGINT DEFAULT NULL AFTER inspector_id,
    ADD CONSTRAINT fk_inspection_template FOREIGN KEY (template_id) REFERENCES check_template(id);

CREATE TABLE inspection_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inspection_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_title VARCHAR(200) NOT NULL,
    result VARCHAR(20) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uq_inspection_item UNIQUE (inspection_id, item_id),
    CONSTRAINT fk_inspection_result_task FOREIGN KEY (inspection_id) REFERENCES inspection(id),
    CONSTRAINT fk_inspection_result_item FOREIGN KEY (item_id) REFERENCES check_item(id)
);

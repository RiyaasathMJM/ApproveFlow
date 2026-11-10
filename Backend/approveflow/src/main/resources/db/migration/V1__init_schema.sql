-- USERS & ROLES
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (role IN ('ADMIN','REQUESTER','MANAGER','FINANCE'))
) ENGINE=InnoDB;

-- WORKFLOW CONFIGURATION
CREATE TABLE workflow_definitions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    request_type       VARCHAR(30)  NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    current_version_id BIGINT       NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_workflow_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE workflow_versions (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_definition_id BIGINT      NOT NULL,
    version_number         INT         NOT NULL,
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_wv_definition FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id),
    CONSTRAINT uq_wv_def_version UNIQUE (workflow_definition_id, version_number)
) ENGINE=InnoDB;

ALTER TABLE workflow_definitions
    ADD CONSTRAINT fk_wd_current_version FOREIGN KEY (current_version_id) REFERENCES workflow_versions(id);

CREATE TABLE workflow_steps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id    BIGINT      NOT NULL,
    sequence_no   INT         NOT NULL,
    approver_role VARCHAR(20) NOT NULL,
    condition_json JSON       NULL,
    due_hours     INT         NOT NULL DEFAULT 48,
    CONSTRAINT fk_ws_version FOREIGN KEY (version_id) REFERENCES workflow_versions(id),
    CONSTRAINT uq_ws_version_seq UNIQUE (version_id, sequence_no),
    CONSTRAINT ck_ws_role CHECK (approver_role IN ('MANAGER','FINANCE','ADMIN'))
) ENGINE=InnoDB;

-- REQUESTS
CREATE TABLE requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id            BIGINT       NOT NULL,
    workflow_version_id BIGINT       NULL,      -- set on first submit
    type                VARCHAR(30)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NULL,
    payload_json        JSON         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    current_step_id     BIGINT       NULL,
    cycle               INT          NOT NULL DEFAULT 0,
    current_step_due_at DATETIME(6)  NULL,
    version             BIGINT       NOT NULL DEFAULT 0,  -- optimistic locking (@Version)
    submitted_at        DATETIME(6)  NULL,
    completed_at        DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_req_owner    FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_req_version  FOREIGN KEY (workflow_version_id) REFERENCES workflow_versions(id),
    CONSTRAINT fk_req_step     FOREIGN KEY (current_step_id) REFERENCES workflow_steps(id),
    CONSTRAINT ck_req_status   CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','RETURNED')),
    INDEX idx_req_status (status),
    INDEX idx_req_owner (owner_id),
    INDEX idx_req_current_step (current_step_id)
) ENGINE=InnoDB;

-- DECISIONS (unique = duplicate decision prevention)
CREATE TABLE approval_decisions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT      NOT NULL,
    step_id    BIGINT      NOT NULL,
    cycle      INT         NOT NULL,
    actor_id   BIGINT      NOT NULL,
    action     VARCHAR(30) NOT NULL,
    comment    TEXT        NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ad_request FOREIGN KEY (request_id) REFERENCES requests(id),
    CONSTRAINT fk_ad_step    FOREIGN KEY (step_id) REFERENCES workflow_steps(id),
    CONSTRAINT fk_ad_actor   FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT uq_ad_once UNIQUE (request_id, step_id, cycle),
    CONSTRAINT ck_ad_action CHECK (action IN ('APPROVE','REJECT','RETURN_FOR_CHANGES'))
) ENGINE=InnoDB;

-- AUDIT (append-only, app-level)
CREATE TABLE audit_events (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id    BIGINT      NOT NULL,
    actor_id      BIGINT      NULL,   -- null for system/escalation job
    action        VARCHAR(30) NOT NULL,
    from_status   VARCHAR(20) NULL,
    to_status     VARCHAR(20) NULL,
    comment       TEXT        NULL,
    metadata_json JSON        NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ae_request FOREIGN KEY (request_id) REFERENCES requests(id),
    CONSTRAINT fk_ae_actor   FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_ae_request_time (request_id, created_at)
) ENGINE=InnoDB;

-- ESCALATIONS (unique = idempotent job)
CREATE TABLE escalation_records (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id   BIGINT      NOT NULL,
    step_id      BIGINT      NOT NULL,
    cycle        INT         NOT NULL,
    escalated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_er_request FOREIGN KEY (request_id) REFERENCES requests(id),
    CONSTRAINT fk_er_step    FOREIGN KEY (step_id) REFERENCES workflow_steps(id),
    CONSTRAINT uq_er_once UNIQUE (request_id, step_id, cycle)
) ENGINE=InnoDB;
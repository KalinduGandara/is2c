-- FR-014, FR-015, FR-017, FR-018: employee master.
CREATE TABLE employee (
    id                   UUID PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ,
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    deleted_at           TIMESTAMPTZ,
    company_id           UUID         NOT NULL REFERENCES company (id),
    branch_id            UUID         NOT NULL REFERENCES branch (id),
    department_id        UUID         NOT NULL REFERENCES department (id),
    designation_id       UUID         NOT NULL REFERENCES designation (id),
    grade_id             UUID REFERENCES grade (id),
    category_id          UUID         NOT NULL REFERENCES employment_category (id),
    employee_number      VARCHAR(60)  NOT NULL,
    title                VARCHAR(20),
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    preferred_name       VARCHAR(100),
    nic                  VARCHAR(30),
    date_of_birth        DATE,
    gender               VARCHAR(10),
    marital_status       VARCHAR(10),
    nationality          VARCHAR(60),
    personal_email       VARCHAR(200),
    mobile               VARCHAR(30),
    address              VARCHAR(500),
    join_date            DATE         NOT NULL,
    probation_end_date   DATE,
    confirmation_date    DATE,
    contract_end_date    DATE,
    exit_date            DATE,
    exit_reason          VARCHAR(500),
    reporting_manager_id UUID REFERENCES employee (id),
    default_shift_id     UUID,
    weekly_off_days      VARCHAR(100),
    badge_id             VARCHAR(50),
    status               VARCHAR(20)  NOT NULL
);
CREATE UNIQUE INDEX uq_employee_number ON employee (company_id, employee_number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_employee_badge ON employee (company_id, badge_id) WHERE deleted_at IS NULL AND badge_id IS NOT NULL;
CREATE UNIQUE INDEX uq_employee_nic ON employee (company_id, nic) WHERE deleted_at IS NULL AND nic IS NOT NULL;
CREATE INDEX ix_employee_branch ON employee (branch_id);
CREATE INDEX ix_employee_department ON employee (department_id);
CREATE INDEX ix_employee_status ON employee (company_id, status);
CREATE INDEX ix_employee_manager ON employee (reporting_manager_id);
CREATE INDEX ix_employee_name ON employee (LOWER(last_name), LOWER(first_name));

CREATE TABLE emergency_contact (
    employee_id  UUID         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    sort_order   INT          NOT NULL,
    name         VARCHAR(200) NOT NULL,
    relationship VARCHAR(60)  NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    PRIMARY KEY (employee_id, sort_order)
);

CREATE TABLE employment_history (
    id             UUID PRIMARY KEY,
    employee_id    UUID         NOT NULL REFERENCES employee (id),
    effective_date DATE         NOT NULL,
    event_type     VARCHAR(20)  NOT NULL,
    changes_json   TEXT         NOT NULL,
    reason         VARCHAR(500),
    recorded_by    UUID,
    recorded_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_employment_history_employee ON employment_history (employee_id, effective_date);

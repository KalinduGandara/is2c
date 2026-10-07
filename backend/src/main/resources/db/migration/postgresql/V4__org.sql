-- FR-010..013, FR-016: organization structure.
CREATE TABLE company (
    id               UUID PRIMARY KEY,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ,
    created_by       UUID,
    updated_at       TIMESTAMPTZ,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    code             VARCHAR(20)  NOT NULL,
    legal_name       VARCHAR(200) NOT NULL,
    registration_no  VARCHAR(50),
    address          VARCHAR(500),
    phone            VARCHAR(30),
    email            VARCHAR(200),
    currency         VARCHAR(3)   NOT NULL,
    epf_employer_no  VARCHAR(50),
    etf_no           VARCHAR(50),
    statutory_pack   VARCHAR(20)  NOT NULL,
    time_zone        VARCHAR(50)  NOT NULL
);
CREATE UNIQUE INDEX uq_company_code ON company (code) WHERE deleted_at IS NULL;

CREATE TABLE company_settings (
    id                       UUID PRIMARY KEY,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ,
    created_by               UUID,
    updated_at               TIMESTAMPTZ,
    updated_by               UUID,
    company_id               UUID         NOT NULL REFERENCES company (id),
    weekly_off_days          VARCHAR(100) NOT NULL,
    employee_number_format   VARCHAR(100) NOT NULL,
    payroll_start_day        INT          NOT NULL,
    absent_as_no_pay         BOOLEAN      NOT NULL,
    no_pay_divisor           NUMERIC(6,2) NOT NULL,
    punch_window_before_min  INT          NOT NULL,
    punch_window_after_min   INT          NOT NULL,
    week_start_day           VARCHAR(10)  NOT NULL,
    CONSTRAINT uq_company_settings UNIQUE (company_id)
);

CREATE TABLE branch (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    company_id UUID         NOT NULL REFERENCES company (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(500),
    phone      VARCHAR(30)
);
CREATE UNIQUE INDEX uq_branch_code ON branch (company_id, code) WHERE deleted_at IS NULL;

CREATE TABLE department (
    id                  UUID PRIMARY KEY,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ,
    created_by          UUID,
    updated_at          TIMESTAMPTZ,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    company_id          UUID         NOT NULL REFERENCES company (id),
    parent_id           UUID REFERENCES department (id),
    branch_id           UUID REFERENCES branch (id),
    code                VARCHAR(20)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    cost_center_code    VARCHAR(50),
    manager_employee_id UUID
);
CREATE UNIQUE INDEX uq_department_code ON department (company_id, code) WHERE deleted_at IS NULL;
CREATE INDEX ix_department_parent ON department (parent_id);

CREATE TABLE designation (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    company_id UUID         NOT NULL REFERENCES company (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(200) NOT NULL
);
CREATE UNIQUE INDEX uq_designation_code ON designation (company_id, code) WHERE deleted_at IS NULL;

CREATE TABLE grade (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    company_id UUID         NOT NULL REFERENCES company (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    rank_no    INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_grade_code ON grade (company_id, code) WHERE deleted_at IS NULL;

CREATE TABLE employment_category (
    id          UUID PRIMARY KEY,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ,
    created_by  UUID,
    updated_at  TIMESTAMPTZ,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    company_id  UUID         NOT NULL REFERENCES company (id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    ot_eligible BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_employment_category_code ON employment_category (company_id, code) WHERE deleted_at IS NULL;

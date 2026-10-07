-- FR-060..066: leave types, balances (with ledger) and requests.
CREATE TABLE leave_type (
    id                    UUID PRIMARY KEY,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ,
    created_by            UUID,
    updated_at            TIMESTAMPTZ,
    updated_by            UUID,
    company_id            UUID         NOT NULL REFERENCES company (id),
    code                  VARCHAR(20)  NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    paid                  BOOLEAN      NOT NULL,
    annual_days           NUMERIC(6,2) NOT NULL,
    accrual_method        VARCHAR(20)  NOT NULL,
    pro_rata              BOOLEAN      NOT NULL,
    cf_max_days           NUMERIC(6,2) NOT NULL,
    cf_expiry_months      INT          NOT NULL,
    allow_half_day        BOOLEAN      NOT NULL,
    allow_negative        BOOLEAN      NOT NULL,
    unlimited             BOOLEAN      NOT NULL,
    count_weekly_off      BOOLEAN      NOT NULL,
    count_holiday         BOOLEAN      NOT NULL,
    attachment_above_days NUMERIC(6,2),
    eligible_category_ids VARCHAR(2000),
    color                 VARCHAR(7),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_leave_type_code UNIQUE (company_id, code)
);

CREATE TABLE leave_balance (
    id                 UUID PRIMARY KEY,
    version            BIGINT       NOT NULL DEFAULT 0,
    employee_id        UUID         NOT NULL REFERENCES employee (id),
    leave_type_id      UUID         NOT NULL REFERENCES leave_type (id),
    leave_year         INT          NOT NULL,
    opening            NUMERIC(6,2) NOT NULL DEFAULT 0,
    entitled           NUMERIC(6,2) NOT NULL DEFAULT 0,
    carried_forward    NUMERIC(6,2) NOT NULL DEFAULT 0,
    adjustment         NUMERIC(6,2) NOT NULL DEFAULT 0,
    taken              NUMERIC(6,2) NOT NULL DEFAULT 0,
    pending            NUMERIC(6,2) NOT NULL DEFAULT 0,
    expired            NUMERIC(6,2) NOT NULL DEFAULT 0,
    last_accrual_month INT,
    CONSTRAINT uq_leave_balance UNIQUE (employee_id, leave_type_id, leave_year)
);

CREATE TABLE leave_balance_txn (
    id         UUID PRIMARY KEY,
    balance_id UUID         NOT NULL REFERENCES leave_balance (id),
    txn_type   VARCHAR(20)  NOT NULL,
    days       NUMERIC(6,2) NOT NULL,
    reference  VARCHAR(100),
    reason     VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_leave_balance_txn_balance ON leave_balance_txn (balance_id, txn_type, reference);

CREATE TABLE leave_request (
    id            UUID PRIMARY KEY,
    version       BIGINT       NOT NULL DEFAULT 0,
    employee_id   UUID         NOT NULL REFERENCES employee (id),
    company_id    UUID         NOT NULL REFERENCES company (id),
    leave_type_id UUID         NOT NULL REFERENCES leave_type (id),
    from_date     DATE         NOT NULL,
    to_date       DATE         NOT NULL,
    half_day_part VARCHAR(10),
    days          NUMERIC(6,2) NOT NULL,
    reason        VARCHAR(1000),
    status        VARCHAR(10)  NOT NULL,
    approval_id   UUID,
    applied_by    UUID,
    applied_at    TIMESTAMPTZ  NOT NULL,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(1000)
);
CREATE INDEX ix_leave_request_employee ON leave_request (employee_id, from_date);
CREATE INDEX ix_leave_request_company ON leave_request (company_id, from_date, to_date);

CREATE TABLE leave_request_day (
    request_id UUID         NOT NULL REFERENCES leave_request (id) ON DELETE CASCADE,
    leave_date DATE         NOT NULL,
    fraction   NUMERIC(3,2) NOT NULL,
    part       VARCHAR(10),
    PRIMARY KEY (request_id, leave_date)
);

CREATE TABLE leave_attachment (
    id           UUID PRIMARY KEY,
    request_id   UUID         NOT NULL REFERENCES leave_request (id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(100) NOT NULL
);

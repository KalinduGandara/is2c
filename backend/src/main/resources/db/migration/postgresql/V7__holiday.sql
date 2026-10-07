-- FR-025 holiday calendar.
CREATE TABLE holiday (
    id           UUID PRIMARY KEY,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ,
    created_by   UUID,
    updated_at   TIMESTAMPTZ,
    updated_by   UUID,
    company_id   UUID         NOT NULL REFERENCES company (id),
    holiday_date DATE         NOT NULL,
    name         VARCHAR(200) NOT NULL,
    holiday_type VARCHAR(20)  NOT NULL,
    all_branches BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_holiday_company_date ON holiday (company_id, holiday_date);

CREATE TABLE holiday_branch (
    holiday_id UUID NOT NULL REFERENCES holiday (id) ON DELETE CASCADE,
    branch_id  UUID NOT NULL REFERENCES branch (id),
    PRIMARY KEY (holiday_id, branch_id)
);

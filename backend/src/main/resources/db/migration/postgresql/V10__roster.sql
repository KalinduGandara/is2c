-- FR-035..039: rosters.
CREATE TABLE roster (
    id            UUID PRIMARY KEY,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_at    TIMESTAMPTZ,
    updated_by    UUID,
    company_id    UUID         NOT NULL REFERENCES company (id),
    department_id UUID REFERENCES department (id),
    name          VARCHAR(100) NOT NULL,
    period_type   VARCHAR(10)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    revision      INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_roster_company_period ON roster (company_id, start_date, end_date);

CREATE TABLE roster_entry (
    id          UUID PRIMARY KEY,
    roster_id   UUID    NOT NULL REFERENCES roster (id) ON DELETE CASCADE,
    employee_id UUID    NOT NULL REFERENCES employee (id),
    work_date   DATE    NOT NULL,
    shift_id    UUID REFERENCES shift (id),
    CONSTRAINT uq_roster_entry UNIQUE (roster_id, employee_id, work_date)
);
CREATE INDEX ix_roster_entry_employee_date ON roster_entry (employee_id, work_date);

CREATE TABLE roster_revision (
    id          UUID PRIMARY KEY,
    roster_id   UUID         NOT NULL REFERENCES roster (id) ON DELETE CASCADE,
    revision    INT          NOT NULL,
    employee_id UUID         NOT NULL,
    work_date   DATE         NOT NULL,
    old_value   VARCHAR(30),
    new_value   VARCHAR(30),
    reason      VARCHAR(500) NOT NULL,
    changed_by  UUID,
    changed_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_roster_revision_roster ON roster_revision (roster_id, revision);

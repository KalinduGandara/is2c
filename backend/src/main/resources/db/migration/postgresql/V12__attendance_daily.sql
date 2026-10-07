-- FR-048..056: processed daily attendance and manual adjustments.
CREATE TABLE attendance_daily (
    id                  UUID PRIMARY KEY,
    employee_id         UUID          NOT NULL REFERENCES employee (id),
    company_id          UUID          NOT NULL REFERENCES company (id),
    work_date           DATE          NOT NULL,
    shift_id            UUID REFERENCES shift (id),
    day_type            VARCHAR(12)   NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    first_in            TIMESTAMP,
    last_out            TIMESTAMP,
    scheduled_min       INT           NOT NULL,
    worked_min          INT           NOT NULL,
    late_min            INT           NOT NULL,
    early_min           INT           NOT NULL,
    short_min           INT           NOT NULL,
    ot_workday_min      INT           NOT NULL,
    ot_offday_min       INT           NOT NULL,
    ot_holiday_min      INT           NOT NULL,
    extra_min           INT           NOT NULL,
    ot_requires_approval BOOLEAN      NOT NULL,
    ot_approved         BOOLEAN       NOT NULL DEFAULT FALSE,
    ot_rejected         BOOLEAN       NOT NULL DEFAULT FALSE,
    absent_fraction     NUMERIC(3,2)  NOT NULL,
    leave_fraction      NUMERIC(3,2)  NOT NULL,
    leave_paid          BOOLEAN       NOT NULL,
    leave_request_id    UUID,
    manual              BOOLEAN       NOT NULL,
    exceptions          VARCHAR(200)  NOT NULL,
    processed_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_attendance_daily UNIQUE (employee_id, work_date)
);
CREATE INDEX ix_attendance_daily_company_date ON attendance_daily (company_id, work_date);

CREATE TABLE attendance_adjustment (
    id             UUID PRIMARY KEY,
    employee_id    UUID          NOT NULL REFERENCES employee (id),
    company_id     UUID          NOT NULL REFERENCES company (id),
    work_date      DATE          NOT NULL,
    adj_type       VARCHAR(10)   NOT NULL,
    new_in         TIMESTAMP,
    new_out        TIMESTAMP,
    before_in      TIMESTAMP,
    before_out     TIMESTAMP,
    before_status  VARCHAR(16),
    reason         VARCHAR(500)  NOT NULL,
    status         VARCHAR(10)   NOT NULL,
    override_lock  BOOLEAN       NOT NULL DEFAULT FALSE,
    approval_id    UUID,
    requested_by   UUID,
    requested_at   TIMESTAMPTZ   NOT NULL,
    decided_at     TIMESTAMPTZ
);
CREATE INDEX ix_attendance_adjustment_employee_date ON attendance_adjustment (employee_id, work_date);

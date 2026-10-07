-- FR-082..089: salary structures, periods, one-time items, results.
CREATE TABLE salary_structure (
    id             UUID PRIMARY KEY,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ,
    created_by     UUID,
    updated_at     TIMESTAMPTZ,
    updated_by     UUID,
    employee_id    UUID          NOT NULL REFERENCES employee (id),
    company_id     UUID          NOT NULL REFERENCES company (id),
    effective_from DATE          NOT NULL,
    effective_to   DATE,
    basic_salary   NUMERIC(15,2) NOT NULL,
    CONSTRAINT uq_salary_structure_start UNIQUE (employee_id, effective_from)
);
CREATE INDEX ix_salary_structure_employee ON salary_structure (employee_id, effective_from);

CREATE TABLE salary_structure_line (
    structure_id        UUID          NOT NULL REFERENCES salary_structure (id) ON DELETE CASCADE,
    line_index          INT           NOT NULL,
    component_id        UUID          NOT NULL REFERENCES payroll_component (id),
    amount              NUMERIC(15,2),
    rate_override       NUMERIC(9,4),
    multiplier_override NUMERIC(9,4),
    excluded            BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (structure_id, line_index)
);

CREATE TABLE payroll_period (
    id           UUID PRIMARY KEY,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ,
    created_by   UUID,
    updated_at   TIMESTAMPTZ,
    updated_by   UUID,
    company_id   UUID        NOT NULL REFERENCES company (id),
    period_year  INT         NOT NULL,
    period_month INT         NOT NULL,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    status       VARCHAR(12) NOT NULL,
    stale        BOOLEAN     NOT NULL DEFAULT FALSE,
    approval_id  UUID,
    processed_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    finalized_by UUID,
    CONSTRAINT uq_payroll_period UNIQUE (company_id, period_year, period_month)
);
CREATE INDEX ix_payroll_period_dates ON payroll_period (company_id, start_date, end_date);

CREATE TABLE payroll_one_time_item (
    id           UUID PRIMARY KEY,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ,
    created_by   UUID,
    updated_at   TIMESTAMPTZ,
    updated_by   UUID,
    period_id    UUID          NOT NULL REFERENCES payroll_period (id),
    employee_id  UUID          NOT NULL REFERENCES employee (id),
    component_id UUID          NOT NULL REFERENCES payroll_component (id),
    amount       NUMERIC(15,2) NOT NULL,
    note         VARCHAR(500)
);
CREATE INDEX ix_payroll_item_period ON payroll_one_time_item (period_id, employee_id);

CREATE TABLE payroll_result (
    id                     UUID PRIMARY KEY,
    period_id              UUID          NOT NULL REFERENCES payroll_period (id),
    employee_id            UUID          NOT NULL REFERENCES employee (id),
    employee_number        VARCHAR(60)   NOT NULL,
    employee_name          VARCHAR(220)  NOT NULL,
    department_name        VARCHAR(200),
    designation_name       VARCHAR(200),
    department_id          UUID,
    gross                  NUMERIC(15,2) NOT NULL,
    deductions             NUMERIC(15,2) NOT NULL,
    net                    NUMERIC(15,2) NOT NULL,
    employer_contributions NUMERIC(15,2) NOT NULL,
    worked_days            NUMERIC(6,2)  NOT NULL,
    no_pay_days            NUMERIC(6,2)  NOT NULL,
    ot_hours               NUMERIC(8,2)  NOT NULL,
    payslip_no             VARCHAR(40),
    locked                 BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_payroll_result UNIQUE (period_id, employee_id)
);

CREATE TABLE payroll_result_line (
    id              UUID PRIMARY KEY,
    result_id       UUID          NOT NULL REFERENCES payroll_result (id),
    sort_order      INT           NOT NULL,
    component_code  VARCHAR(30)   NOT NULL,
    component_name  VARCHAR(100)  NOT NULL,
    kind            VARCHAR(25)   NOT NULL,
    units           NUMERIC(10,2),
    rate            NUMERIC(15,4),
    amount          NUMERIC(15,2) NOT NULL,
    show_on_payslip BOOLEAN       NOT NULL,
    locked          BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_payroll_result_line_result ON payroll_result_line (result_id, sort_order);

CREATE TABLE payroll_exception (
    id          UUID PRIMARY KEY,
    period_id   UUID         NOT NULL REFERENCES payroll_period (id),
    employee_id UUID REFERENCES employee (id),
    code        VARCHAR(40)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    blocking    BOOLEAN      NOT NULL
);
CREATE INDEX ix_payroll_exception_period ON payroll_exception (period_id);

-- FR-087: finalized payroll rows are immutable at the database level.
CREATE FUNCTION payroll_locked_guard() RETURNS trigger AS $$
BEGIN
    IF OLD.locked THEN
        RAISE EXCEPTION 'payroll results are finalized and locked (% rejected)', TG_OP;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_result_locked BEFORE UPDATE OR DELETE ON payroll_result
    FOR EACH ROW EXECUTE FUNCTION payroll_locked_guard();
CREATE TRIGGER trg_payroll_result_line_locked BEFORE UPDATE OR DELETE ON payroll_result_line
    FOR EACH ROW EXECUTE FUNCTION payroll_locked_guard();

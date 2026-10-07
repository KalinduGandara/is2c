-- FR-080/081: payroll components and slab tables.
CREATE TABLE slab_table (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    company_id UUID         NOT NULL REFERENCES company (id),
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    statutory  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_slab_table_code UNIQUE (company_id, code)
);

CREATE TABLE slab_row (
    table_id     UUID          NOT NULL REFERENCES slab_table (id) ON DELETE CASCADE,
    row_index    INT           NOT NULL,
    lower_bound  NUMERIC(15,2) NOT NULL,
    upper_bound  NUMERIC(15,2),
    rate_percent NUMERIC(9,4)  NOT NULL,
    PRIMARY KEY (table_id, row_index)
);

CREATE TABLE payroll_component (
    id                UUID PRIMARY KEY,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ,
    created_by        UUID,
    updated_at        TIMESTAMPTZ,
    updated_by        UUID,
    company_id        UUID          NOT NULL REFERENCES company (id),
    code              VARCHAR(30)   NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    kind              VARCHAR(25)   NOT NULL,
    frequency         VARCHAR(10)   NOT NULL,
    assignment        VARCHAR(15)   NOT NULL,
    calc_type         VARCHAR(12)   NOT NULL,
    sequence          INT           NOT NULL,
    bases             VARCHAR(200)  NOT NULL DEFAULT '',
    basis_ref         VARCHAR(60),
    rate_percent      NUMERIC(9,4),
    unit_metric       VARCHAR(25),
    unit_rate_mode    VARCHAR(15),
    unit_fixed_amount NUMERIC(15,2),
    divisor           NUMERIC(9,4),
    multiplier        NUMERIC(9,4),
    slab_table_id     UUID REFERENCES slab_table (id),
    prorate           BOOLEAN       NOT NULL DEFAULT FALSE,
    show_on_payslip   BOOLEAN       NOT NULL DEFAULT TRUE,
    statutory         BOOLEAN       NOT NULL DEFAULT FALSE,
    system_component  BOOLEAN       NOT NULL DEFAULT FALSE,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_payroll_component_code UNIQUE (company_id, code)
);

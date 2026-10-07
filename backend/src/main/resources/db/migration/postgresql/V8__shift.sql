-- FR-030..032: shifts, OT policies, rotation patterns.
CREATE TABLE ot_policy (
    id                 UUID PRIMARY KEY,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ,
    created_by         UUID,
    updated_at         TIMESTAMPTZ,
    updated_by         UUID,
    company_id         UUID         NOT NULL REFERENCES company (id),
    name               VARCHAR(100) NOT NULL,
    min_ot_minutes     INT          NOT NULL,
    rounding_minutes   INT          NOT NULL,
    allow_pre_shift_ot BOOLEAN      NOT NULL,
    max_ot_minutes     INT,
    requires_approval  BOOLEAN      NOT NULL
);

CREATE TABLE shift (
    id                     UUID PRIMARY KEY,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ,
    created_by             UUID,
    updated_at             TIMESTAMPTZ,
    updated_by             UUID,
    company_id             UUID         NOT NULL REFERENCES company (id),
    code                   VARCHAR(20)  NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    shift_type             VARCHAR(10)  NOT NULL,
    color                  VARCHAR(7),
    s1_start               TIME,
    s1_end                 TIME,
    s2_start               TIME,
    s2_end                 TIME,
    flex_earliest_start    TIME,
    flex_latest_end        TIME,
    required_minutes       INT,
    late_grace_min         INT          NOT NULL DEFAULT 0,
    early_grace_min        INT          NOT NULL DEFAULT 0,
    break_mode             VARCHAR(10)  NOT NULL,
    break_minutes          INT          NOT NULL DEFAULT 0,
    break_threshold_min    INT          NOT NULL DEFAULT 0,
    half_day_threshold_min INT,
    ot_policy_id           UUID REFERENCES ot_policy (id),
    window_before_min      INT,
    window_after_min       INT,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_shift_code UNIQUE (company_id, code)
);

ALTER TABLE employee ADD CONSTRAINT fk_employee_default_shift FOREIGN KEY (default_shift_id) REFERENCES shift (id);

CREATE TABLE rotation_pattern (
    id         UUID PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    company_id UUID         NOT NULL REFERENCES company (id),
    name       VARCHAR(100) NOT NULL
);

CREATE TABLE rotation_pattern_day (
    pattern_id UUID NOT NULL REFERENCES rotation_pattern (id) ON DELETE CASCADE,
    day_index  INT  NOT NULL,
    shift_id   UUID REFERENCES shift (id),
    PRIMARY KEY (pattern_id, day_index)
);

CREATE TABLE rotation_assignment (
    id          UUID PRIMARY KEY,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ,
    created_by  UUID,
    updated_at  TIMESTAMPTZ,
    updated_by  UUID,
    employee_id UUID   NOT NULL REFERENCES employee (id),
    pattern_id  UUID   NOT NULL REFERENCES rotation_pattern (id),
    start_date  DATE   NOT NULL,
    end_date    DATE
);
CREATE INDEX ix_rotation_assignment_employee ON rotation_assignment (employee_id, start_date);

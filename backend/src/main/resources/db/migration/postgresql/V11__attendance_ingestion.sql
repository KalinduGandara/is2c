-- FR-045..047: attendance sources and raw events.
CREATE TABLE attendance_source (
    id             UUID PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ,
    created_by     UUID,
    updated_at     TIMESTAMPTZ,
    updated_by     UUID,
    company_id     UUID         NOT NULL REFERENCES company (id),
    name           VARCHAR(100) NOT NULL,
    adapter_type   VARCHAR(20)  NOT NULL,
    api_key_prefix VARCHAR(16),
    api_key_hash   VARCHAR(64),
    system_source  BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_attendance_source_key ON attendance_source (api_key_prefix) WHERE api_key_prefix IS NOT NULL;

CREATE TABLE attendance_event (
    id                  UUID PRIMARY KEY,
    source_id           UUID         NOT NULL REFERENCES attendance_source (id),
    company_id          UUID         NOT NULL REFERENCES company (id),
    external_id         VARCHAR(100) NOT NULL,
    device_id           VARCHAR(100),
    employee_identifier VARCHAR(100) NOT NULL,
    employee_id         UUID REFERENCES employee (id),
    event_time          TIMESTAMPTZ  NOT NULL,
    direction           VARCHAR(10)  NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    received_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_attendance_event_external UNIQUE (source_id, external_id)
);
CREATE INDEX ix_attendance_event_employee_time ON attendance_event (employee_id, event_time);
CREATE INDEX ix_attendance_event_unmatched ON attendance_event (company_id, status);

-- FR-019 custom fields, FR-020 documents.
CREATE TABLE custom_field_def (
    id            UUID PRIMARY KEY,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_at    TIMESTAMPTZ,
    updated_by    UUID,
    company_id    UUID         NOT NULL REFERENCES company (id),
    field_key     VARCHAR(50)  NOT NULL,
    label         VARCHAR(100) NOT NULL,
    field_type    VARCHAR(10)  NOT NULL,
    options_csv   VARCHAR(2000),
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_custom_field_key UNIQUE (company_id, field_key)
);

CREATE TABLE custom_field_value (
    employee_id  UUID          NOT NULL REFERENCES employee (id),
    field_def_id UUID          NOT NULL REFERENCES custom_field_def (id),
    value_text   VARCHAR(1000) NOT NULL,
    PRIMARY KEY (employee_id, field_def_id)
);

CREATE TABLE employee_document (
    id           UUID PRIMARY KEY,
    employee_id  UUID         NOT NULL REFERENCES employee (id),
    doc_type     VARCHAR(50)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(100) NOT NULL,
    expiry_date  DATE,
    uploaded_by  UUID,
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX ix_employee_document_employee ON employee_document (employee_id);

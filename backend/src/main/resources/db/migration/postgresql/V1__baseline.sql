-- Baseline: shared infrastructure tables.
CREATE TABLE number_sequence (
    id           UUID PRIMARY KEY,
    company_id   UUID         NOT NULL,
    sequence_key VARCHAR(100) NOT NULL,
    next_value   BIGINT       NOT NULL,
    CONSTRAINT uq_number_sequence UNIQUE (company_id, sequence_key)
);

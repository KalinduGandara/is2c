-- FR-008: append-only audit log.
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    user_id        UUID,
    username       VARCHAR(100),
    on_behalf_of   UUID,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(60)  NOT NULL,
    entity_id      VARCHAR(100),
    company_id     UUID,
    before_json    TEXT,
    after_json     TEXT,
    reason         VARCHAR(1000),
    ip             VARCHAR(64),
    correlation_id VARCHAR(64)
);
CREATE INDEX ix_audit_occurred ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_user ON audit_log (user_id);
CREATE INDEX ix_audit_company ON audit_log (company_id);

-- Immutability is enforced by the database, not only by the application (CL-E2).
CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only (% rejected)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

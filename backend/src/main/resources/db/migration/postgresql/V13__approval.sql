-- FR-070..073: approval workflows, transactions and delegation.
CREATE TABLE approval_workflow (
    id            UUID PRIMARY KEY,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_at    TIMESTAMPTZ,
    updated_by    UUID,
    company_id    UUID        NOT NULL REFERENCES company (id),
    branch_id     UUID REFERENCES branch (id),
    department_id UUID REFERENCES department (id),
    txn_type      VARCHAR(40) NOT NULL,
    enabled       BOOLEAN     NOT NULL
);
-- One workflow per (type, company, branch, department); NULLs compared as equal.
CREATE UNIQUE INDEX uq_approval_workflow_scope ON approval_workflow (
    txn_type, company_id,
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(department_id, '00000000-0000-0000-0000-000000000000'));

CREATE TABLE approval_workflow_level (
    workflow_id   UUID        NOT NULL REFERENCES approval_workflow (id) ON DELETE CASCADE,
    level_index   INT         NOT NULL,
    approver_type VARCHAR(20) NOT NULL,
    role_code     VARCHAR(50),
    user_id       UUID,
    PRIMARY KEY (workflow_id, level_index)
);

CREATE TABLE approval_transaction (
    id                    UUID PRIMARY KEY,
    version               BIGINT       NOT NULL DEFAULT 0,
    txn_type              VARCHAR(40)  NOT NULL,
    reference_id          UUID         NOT NULL,
    company_id            UUID         NOT NULL REFERENCES company (id),
    requester_user_id     UUID,
    subject_employee_id   UUID REFERENCES employee (id),
    status                VARCHAR(10)  NOT NULL,
    current_level         INT          NOT NULL,
    levels_snapshot       TEXT         NOT NULL,
    summary               VARCHAR(500) NOT NULL,
    submitted_at          TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ
);
CREATE INDEX ix_approval_txn_status ON approval_transaction (status, company_id);
CREATE INDEX ix_approval_txn_reference ON approval_transaction (txn_type, reference_id);
CREATE INDEX ix_approval_txn_requester ON approval_transaction (requester_user_id);

CREATE TABLE approval_action (
    id             UUID PRIMARY KEY,
    transaction_id UUID          NOT NULL REFERENCES approval_transaction (id),
    level_no       INT           NOT NULL,
    action         VARCHAR(10)   NOT NULL,
    user_id        UUID,
    on_behalf_of   UUID,
    comment        VARCHAR(1000),
    acted_at       TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_approval_action_txn ON approval_action (transaction_id, acted_at);

CREATE TABLE approval_delegation (
    id                UUID PRIMARY KEY,
    delegator_user_id UUID    NOT NULL REFERENCES app_user (id),
    delegate_user_id  UUID    NOT NULL REFERENCES app_user (id),
    from_date         DATE    NOT NULL,
    to_date           DATE    NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_approval_delegation_delegator ON approval_delegation (delegator_user_id, from_date);

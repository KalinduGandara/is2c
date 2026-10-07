-- FR-001..006: users, roles, permissions, refresh tokens.
CREATE TABLE role (
    id          UUID PRIMARY KEY,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ,
    created_by  UUID,
    updated_at  TIMESTAMPTZ,
    updated_by  UUID,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE role_permission (
    role_id    UUID        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE app_user (
    id                   UUID PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ,
    created_by           UUID,
    updated_at           TIMESTAMPTZ,
    updated_by           UUID,
    username             VARCHAR(100) NOT NULL,
    email                VARCHAR(200),
    display_name         VARCHAR(200) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    employee_id          UUID,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts      INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    scope_type           VARCHAR(20)  NOT NULL DEFAULT 'OWN',
    last_login_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_app_user_username ON app_user (LOWER(username));
CREATE UNIQUE INDEX uq_app_user_employee ON app_user (employee_id);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_scope_item (
    user_id   UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL,
    item_id   UUID        NOT NULL,
    PRIMARY KEY (user_id, item_type, item_id)
);

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Seed system roles (design.md §6.2). SUPER_ADMIN gets every permission implicitly in code.
INSERT INTO role (id, code, name, description, system) VALUES
 ('00000000-0000-7000-8000-000000000001', 'SUPER_ADMIN', 'Super Administrator', 'System configuration and global administration', TRUE),
 ('00000000-0000-7000-8000-000000000002', 'COMPANY_ADMIN', 'Company Administrator', 'Company setup, branches, departments, users and policies', TRUE),
 ('00000000-0000-7000-8000-000000000003', 'HR_MANAGER', 'HR Manager', 'Employee records, leave, attendance corrections, approvals and HR reports', TRUE),
 ('00000000-0000-7000-8000-000000000004', 'PAYROLL_OFFICER', 'Payroll Officer', 'Salary setup, payroll processing, adjustments and payslips', TRUE),
 ('00000000-0000-7000-8000-000000000005', 'BRANCH_MANAGER', 'Branch Manager', 'Branch-level employee visibility, approvals and reports', TRUE),
 ('00000000-0000-7000-8000-000000000006', 'DEPARTMENT_MANAGER', 'Department Manager', 'Team roster, leave approvals and attendance review', TRUE),
 ('00000000-0000-7000-8000-000000000007', 'SUPERVISOR', 'Supervisor', 'Roster management and first-level approvals where permitted', TRUE),
 ('00000000-0000-7000-8000-000000000008', 'EMPLOYEE', 'Employee', 'Self-service access to own profile, attendance, leave and payslips', TRUE),
 ('00000000-0000-7000-8000-000000000009', 'AUDITOR', 'Auditor', 'Read-only access to selected modules and audit logs', TRUE);

CREATE TEMPORARY TABLE seed_perm (role_code VARCHAR(50), module VARCHAR(20), actions VARCHAR(200));
INSERT INTO seed_perm VALUES
 ('COMPANY_ADMIN', 'ORG', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'EMPLOYEE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'ATTENDANCE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'SHIFT', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'ROSTER', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'LEAVE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'APPROVAL', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('COMPANY_ADMIN', 'REPORT', 'VIEW,EXPORT'),
 ('COMPANY_ADMIN', 'AUDIT', 'VIEW'),
 ('COMPANY_ADMIN', 'ADMIN', 'VIEW,ADD,EDIT,DELETE,CONFIGURE'),
 ('HR_MANAGER', 'ORG', 'VIEW'),
 ('HR_MANAGER', 'EMPLOYEE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('HR_MANAGER', 'ATTENDANCE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('HR_MANAGER', 'SHIFT', 'VIEW,ADD,EDIT'),
 ('HR_MANAGER', 'ROSTER', 'VIEW,ADD,EDIT,APPROVE'),
 ('HR_MANAGER', 'LEAVE', 'VIEW,ADD,EDIT,DELETE,APPROVE,EXPORT,CONFIGURE'),
 ('HR_MANAGER', 'APPROVAL', 'VIEW,APPROVE'),
 ('HR_MANAGER', 'PAYROLL', 'VIEW,APPROVE,EXPORT'),
 ('HR_MANAGER', 'REPORT', 'VIEW,EXPORT'),
 ('PAYROLL_OFFICER', 'ORG', 'VIEW'),
 ('PAYROLL_OFFICER', 'EMPLOYEE', 'VIEW'),
 ('PAYROLL_OFFICER', 'ATTENDANCE', 'VIEW'),
 ('PAYROLL_OFFICER', 'LEAVE', 'VIEW'),
 ('PAYROLL_OFFICER', 'PAYROLL', 'VIEW,ADD,EDIT,DELETE,EXPORT,CONFIGURE'),
 ('PAYROLL_OFFICER', 'REPORT', 'VIEW,EXPORT'),
 ('BRANCH_MANAGER', 'ORG', 'VIEW'),
 ('BRANCH_MANAGER', 'EMPLOYEE', 'VIEW'),
 ('BRANCH_MANAGER', 'ATTENDANCE', 'VIEW,APPROVE'),
 ('BRANCH_MANAGER', 'SHIFT', 'VIEW'),
 ('BRANCH_MANAGER', 'ROSTER', 'VIEW,APPROVE'),
 ('BRANCH_MANAGER', 'LEAVE', 'VIEW,APPROVE'),
 ('BRANCH_MANAGER', 'APPROVAL', 'VIEW'),
 ('BRANCH_MANAGER', 'REPORT', 'VIEW,EXPORT'),
 ('DEPARTMENT_MANAGER', 'ORG', 'VIEW'),
 ('DEPARTMENT_MANAGER', 'EMPLOYEE', 'VIEW'),
 ('DEPARTMENT_MANAGER', 'ATTENDANCE', 'VIEW,APPROVE'),
 ('DEPARTMENT_MANAGER', 'SHIFT', 'VIEW'),
 ('DEPARTMENT_MANAGER', 'ROSTER', 'VIEW,ADD,EDIT,APPROVE'),
 ('DEPARTMENT_MANAGER', 'LEAVE', 'VIEW,APPROVE'),
 ('DEPARTMENT_MANAGER', 'APPROVAL', 'VIEW'),
 ('DEPARTMENT_MANAGER', 'REPORT', 'VIEW'),
 ('SUPERVISOR', 'ORG', 'VIEW'),
 ('SUPERVISOR', 'EMPLOYEE', 'VIEW'),
 ('SUPERVISOR', 'ATTENDANCE', 'VIEW'),
 ('SUPERVISOR', 'SHIFT', 'VIEW'),
 ('SUPERVISOR', 'ROSTER', 'VIEW,ADD,EDIT'),
 ('SUPERVISOR', 'LEAVE', 'VIEW'),
 ('AUDITOR', 'ORG', 'VIEW,EXPORT'),
 ('AUDITOR', 'EMPLOYEE', 'VIEW,EXPORT'),
 ('AUDITOR', 'ATTENDANCE', 'VIEW,EXPORT'),
 ('AUDITOR', 'SHIFT', 'VIEW,EXPORT'),
 ('AUDITOR', 'ROSTER', 'VIEW,EXPORT'),
 ('AUDITOR', 'LEAVE', 'VIEW,EXPORT'),
 ('AUDITOR', 'APPROVAL', 'VIEW,EXPORT'),
 ('AUDITOR', 'REPORT', 'VIEW,EXPORT'),
 ('AUDITOR', 'AUDIT', 'VIEW,EXPORT');

INSERT INTO role_permission (role_id, permission)
SELECT r.id, s.module || ':' || a.action
FROM seed_perm s
JOIN role r ON r.code = s.role_code
CROSS JOIN LATERAL unnest(string_to_array(s.actions, ',')) AS a(action);

DROP TABLE seed_perm;

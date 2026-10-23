-- Baseline schema for the user domain (User, Role, Permission, UserRole,
-- RefreshToken, LoginHistory, Device, AuditLog) plus RevokedToken and
-- PasswordResetToken. This is a fresh baseline, not an ALTER on top of the
-- previous ddl-auto:update schema - see docs/database.md and the design
-- notes in this change for why (no production data to preserve).
--
-- Soft-deletable tables (users, roles, permissions, user_roles, devices) use
-- PARTIAL unique indexes (WHERE deleted_at IS NULL) instead of plain UNIQUE
-- columns/constraints, so a soft-deleted row's email/name/etc can be reused
-- by a new row without a full unique constraint violation.

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    phone_number          VARCHAR(32),
    password              VARCHAR(255),
    name                  VARCHAR(255),
    image_url             VARCHAR(1024),
    email_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    provider              VARCHAR(32),
    provider_id           VARCHAR(255),
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    mfa_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret            VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    deleted_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_users_phone_number ON users (phone_number)
    WHERE deleted_at IS NULL AND phone_number IS NOT NULL;
CREATE INDEX idx_users_provider ON users (provider, provider_id);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_roles_name ON roles (name) WHERE deleted_at IS NULL;

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    resource    VARCHAR(255) NOT NULL,
    action      VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_permissions_name ON permissions (name) WHERE deleted_at IS NULL;
CREATE INDEX idx_permissions_resource_action ON permissions (resource, action);

-- Coarse Role<->Permission grant. No audit metadata (who/when) unlike
-- user_roles below - see design notes on why that asymmetry is intentional.
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

-- Explicit User<->Role bridge (not a bare join table) so a grant carries who
-- granted it and when (assigned_by / created_at).
CREATE TABLE user_roles (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    role_id     UUID NOT NULL REFERENCES roles (id),
    assigned_by UUID,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_user_roles_user_role ON user_roles (user_id, role_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);

CREATE TABLE devices (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id),
    fingerprint     VARCHAR(255) NOT NULL,
    device_name     VARCHAR(255),
    device_type     VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    user_agent      VARCHAR(1024),
    last_ip_address VARCHAR(64),
    trusted         BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_devices_user_fingerprint ON devices (user_id, fingerprint)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_devices_user_id ON devices (user_id);

-- No soft delete: "revoked" is this entity's own logical-delete flag.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMPTZ NOT NULL,
    user_id    UUID NOT NULL REFERENCES users (id),
    device_id  UUID REFERENCES devices (id),
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens (expiry_date);

CREATE TABLE revoked_tokens (
    id          UUID PRIMARY KEY,
    jti         VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_revoked_tokens_jti ON revoked_tokens (jti);
CREATE INDEX idx_revoked_tokens_expiry_date ON revoked_tokens (expiry_date);

CREATE TABLE password_reset_tokens (
    id                UUID PRIMARY KEY,
    token             VARCHAR(255) NOT NULL,
    user_id           UUID NOT NULL REFERENCES users (id),
    expiry_date_time  TIMESTAMP NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_password_reset_tokens_token ON password_reset_tokens (token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);

-- Append-only: no updated_at/version/deleted_at (see AuditBaseEntity).
CREATE TABLE login_history (
    id             UUID PRIMARY KEY,
    user_id        UUID REFERENCES users (id),
    device_id      UUID REFERENCES devices (id),
    identifier     VARCHAR(255),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(1024),
    success        BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_history_user_created ON login_history (user_id, created_at);
CREATE INDEX idx_login_history_ip_address ON login_history (ip_address);

-- Append-only broad compliance/audit trail (signup, MFA, password reset,
-- token revocation, etc - see AuditEventType).
CREATE TABLE audit_logs (
    id         UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    user_id    UUID REFERENCES users (id),
    identifier VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(1024),
    details    VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs (user_id, created_at);
CREATE INDEX idx_audit_logs_event_type ON audit_logs (event_type);

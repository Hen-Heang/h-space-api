-- One-time MFA backup/recovery codes, generated when MFA is enabled or
-- explicitly regenerated. Only the BCrypt hash is ever persisted (see
-- MfaRecoveryCode / MfaBackupCodeService) - the plaintext is shown to the
-- user once and never stored.

CREATE TABLE mfa_recovery_codes (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id),
    code_hash  VARCHAR(255) NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_mfa_recovery_codes_user_id ON mfa_recovery_codes (user_id);

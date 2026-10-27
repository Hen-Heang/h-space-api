-- Two more ways to sign in, alongside email + password and Google:
--
-- 1. Username + password. Optional per account (Google and email-code
--    accounts have none); stored lowercase and unique case-insensitively
--    among live rows, like the other soft-delete-aware indexes in V1.
-- 2. "Continue with email": a 6-digit code emailed on request. One live code
--    per address (a new request replaces it); only its SHA-256 hash is
--    stored, and the row is deleted once used or after too many attempts.

ALTER TABLE users ADD COLUMN username VARCHAR(30);

CREATE UNIQUE INDEX idx_users_username ON users (lower(username))
    WHERE deleted_at IS NULL AND username IS NOT NULL;

CREATE TABLE email_login_codes (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_email_login_codes_email ON email_login_codes (email);

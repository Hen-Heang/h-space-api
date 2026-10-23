-- One-time, emailed security tokens for the account-security feature set.
-- Same shape as password_reset_tokens (V1) - single-purpose table per token
-- type, row deleted immediately on successful use.

CREATE TABLE email_verification_tokens (
    id               UUID PRIMARY KEY,
    token            VARCHAR(255) NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id),
    expiry_date_time TIMESTAMP NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_email_verification_tokens_token ON email_verification_tokens (token);
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);

CREATE TABLE account_unlock_tokens (
    id               UUID PRIMARY KEY,
    token            VARCHAR(255) NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id),
    expiry_date_time TIMESTAMP NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_account_unlock_tokens_token ON account_unlock_tokens (token);
CREATE INDEX idx_account_unlock_tokens_user_id ON account_unlock_tokens (user_id);

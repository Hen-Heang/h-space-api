-- Invite-only sign-up (app.signup.mode=invite-only): an account can only be
-- created for an email with a pending invite here, or for the owner
-- (app.owner-email). An invite is used up by the account it creates; at most
-- one pending invite per (lowercased) email.

CREATE TABLE signup_invites (
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    invited_by UUID REFERENCES users (id),
    used_at    TIMESTAMPTZ,
    used_by    UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_signup_invites_pending_email ON signup_invites (email)
    WHERE used_at IS NULL;

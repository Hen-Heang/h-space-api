-- TOTP time step of the last authenticator code accepted at sign-in. A code
-- stays valid for about a minute and a half; remembering its step lets
-- sign-in refuse the same code (or an older one) a second time.

ALTER TABLE users ADD COLUMN mfa_last_used_step BIGINT;

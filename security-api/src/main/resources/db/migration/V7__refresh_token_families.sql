-- Refresh-token families, for reuse (theft) detection.
--
-- Every sign-in starts a family; each rotation hands the new token the same
-- family_id and stamps the spent token's rotated_at. A token that was spent by
-- rotation and is presented again means two parties hold the same chain - the
-- legitimate client and whoever copied a token - so the whole family is
-- revoked. Tokens revoked any other way (logout, session cap, password reset)
-- keep rotated_at NULL and are simply refused.
--
-- Existing tokens become their own one-token family.

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = id;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

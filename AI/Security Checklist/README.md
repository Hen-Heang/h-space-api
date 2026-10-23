# Security Checklist

Checklist to run through before shipping auth-related changes in `security-api` — JWT
issuance/validation, MFA, token revocation, rate limiting, Google ID-token verification,
password reset flows. Pair with the `security-reviewer` agent for a deeper pass.

- [ ] Secrets not hard-coded / committed (`jwt.secret` via env var outside local dev)
- [ ] New endpoints covered by `RateLimitingFilter` where appropriate
- [ ] Tokens invalidated correctly on logout/refresh (check `TokenBlacklistService`)
- [ ] Sensitive actions produce an `AuditEvent`
- [ ] No reintroduction of removed OTP / cookie-based OAuth2 code (see `docs/security-api-state.md`)
- [ ] Exceptions thrown as domain exceptions, not caught-and-formatted in controllers

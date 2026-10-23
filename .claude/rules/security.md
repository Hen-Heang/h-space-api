# Security Rule

Full detail: `docs/database.md`, `docs/security-api-state.md`, `SECURITY.md`.

## Secrets

- `jwt.secret` and DB credentials are hard-coded only in local
  `application*.yml` for dev convenience — never commit a real secret.
  Anything beyond local dev must read `JWT_SECRET` (and other secrets) from
  environment variables.
- `application-local.yml` and `.env` are git-ignored — that's where local
  overrides belong, not in a tracked file.
- If you ever find a real-looking secret in a tracked file, treat it as a
  historical-exposure incident per `SECURITY.md` (rotate, don't just delete
  the line) rather than silently scrubbing it.

## `security-api` auth model — what's in, what's out

This module is mid-refactor; don't trust old README history — check current
files first (`Glob security-api/src/main/java/**/*.java`).

- **In**: JWT login/refresh/logout (`AuthController`), MFA
  (`MfaService`/`MfaController`), Google ID-token login
  (`security/oauth/GoogleTokenVerifier` — verification only, not a
  cookie-based redirect flow), audit events (`AuditEvent`/`AuditLogService`),
  token revocation (`RevokedToken`/`TokenBlacklistService`),
  `RateLimitingFilter`.
- **Out — do not resurrect without asking**: custom OTP
  controller/service/domain/payloads, and the old cookie-based OAuth2 flow
  (`HttpCookieOAuth2AuthorizationRequestRepository`, `OAuth2*Handler`,
  `OAuth2UserInfo*`, `CookieUtils`).
- Stale references to either (in code, docs, or tests) should be flagged as
  drift, not treated as the current pattern to follow.

## Reporting

Security issues go through `SECURITY.md`'s process, not a public GitHub
issue.

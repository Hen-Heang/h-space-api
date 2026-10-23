# Current state of `security-api` (auth model)

This module is mid-refactor. Do not assume old README history — check current files first (`Glob security-api/src/main/java/**/*.java`) since this section can drift.

- **In**: `AuthController`, `UserController`, `AuditController`, `MfaController`, `PublicController` — JWT login/refresh/logout, MFA (`MfaService`/`MfaController`), Google login via `security/oauth/GoogleTokenVerifier` (ID-token verification, not a cookie-based OAuth2 redirect flow), audit events (`AuditEvent`, `AuditLogService`), token revocation (`RevokedToken`, `TokenBlacklistService`), `RateLimitingFilter`.
- **Out** (removed, do not resurrect without asking): custom OTP controller/service/domain/payloads, the old cookie-based OAuth2 flow (`HttpCookieOAuth2AuthorizationRequestRepository`, `OAuth2*Handler`, `OAuth2UserInfo*`, `CookieUtils`).
- If you see references to OTP or the old OAuth2 classes anywhere (code, docs, tests), treat that as stale and flag it rather than silently reintroducing the pattern.

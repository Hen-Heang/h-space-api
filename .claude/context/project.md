# Project Context

AuthHub is a multi-module Spring Boot monorepo providing a reusable
authentication service (`security-api`) plus a sample business API
(`open-api`) that demonstrates consuming it. `common-api` is the shared base
library both depend on.

Core capabilities in `security-api`: JWT auth (issue/refresh/revocation),
Google ID-token login, MFA (TOTP + one-time backup codes), email
verification, password reset, account lockout with self-service unlock,
RBAC (roles/permissions), audit logging, rate limiting, Swagger UI.

## Where this stands right now

- `main` has an **in-progress refactor**: removing the old custom OTP
  system and the old cookie-based OAuth2 redirect flow, while adding/
  completing MFA, audit logging, and rate limiting. See
  `docs/security-api-state.md` for the authoritative in/out list — it can
  drift from actual code, so re-check before trusting it fully.
- Foundation/tooling work (Gradle cleanup, Spotless/Checkstyle/PMD/JaCoCo,
  CI, secrets externalization, standard docs) is done — see `ROADMAP.md`
  "Done" section and `.claude/context/roadmap.md`.
- No versioned releases yet — everything is `0.0.1-SNAPSHOT`, changes
  accumulate under `CHANGELOG.md`'s `[Unreleased]` section.
- Feature work beyond the OTP/OAuth2 refactor is explicitly deferred until
  the foundation pass is reviewed (`ROADMAP.md`).

## Known, deliberate rough edges (don't "fix" without asking)

- Checkstyle/PMD are advisory only (`ignoreFailures = true`) so the existing
  codebase doesn't fail `./gradlew build`.
- No JaCoCo coverage gate yet — reports are generated, not enforced.

## Related context files

`.claude/context/modules.md` (module/package breakdown),
`.claude/context/database.md` (Flyway/schema), `.claude/context/api-style.md`
(response envelope pattern), `.claude/context/tech-stack.md` (dependency
versions), `.claude/context/roadmap.md` (what's done vs. deferred).

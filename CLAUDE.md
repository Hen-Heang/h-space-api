# H-Space API

Multi-module Gradle/Spring Boot monorepo. Group `com.henheang`, Java 17, Spring Boot 3.5.15, PostgreSQL.

@docs/architecture.md
@docs/security-api-state.md
@docs/coding-standards.md
@docs/database.md
@docs/build-and-run.md

## Git Rules

- Never commit unless explicitly asked.
- This repo currently has an in-progress refactor on `main` (OTP/OAuth2 removal, MFA/audit/rate-limiting addition) — before staging files, check `git status` and don't sweep unrelated in-progress changes into a new commit.

## Product mission

The API is the security and backend-engineering foundation for **H-Space**, a private-by-default daily personal hub. Its core experiences are My Apps, goals/tasks, reminders, finance integration (Luyra), Hengo Learning, and a **future** credential vault. The objective is an actually useful daily app and disciplined Java/Spring Boot/PostgreSQL/DevOps practice, not feature-count-driven demos. Cross-repo context: [H-Space web](https://github.com/Hen-Heang/h-space) and [H-Space Notion hub](https://app.notion.com/p/3b9acd8b74508151ac22ecff24718832).

## Boundaries and current integration

- `api-core` -> `security-api` -> `open-api` is the one-way module dependency order. Do not reintroduce `legacy/spring-jwt-auth`.
- `security-api` currently handles web authentication (email/username password, emailed sign-in code, verified Google ID token, short-lived JWT, rotating refresh tokens, optional TOTP MFA, backup codes, RBAC, rate limiting, and audit logs). **Read current implementation and `docs/security-api-state.md` before modifying auth.** Do not resurrect removed custom OTP or cookie-based OAuth2 code.
- Existing H-Space web feature records are still in Supabase with RLS, not automatically owned by this API. The Supabase JWT bridge and account import are temporary migration compatibility. `open-api` todo endpoints are a **sample**, not a verified frontend integration.
- Before moving goals, tasks, reminders, finances, or vault records to Spring, document who owns the data, the API contract, UUID/user mapping, RLS/authorization replacement, reconciliation and rollback plan; implement a small tested vertical slice first.
- Notion is for decisions/planning only, never runtime data or secrets.

## Vault and authentication safety gate

- A real credential vault must **not** be shipped from an ordinary CRUD schema. First document a threat model, cross-device recovery model, encryption boundaries, and obtain independent cryptographic/security review. Never save or log real vault passwords before that review.
- Separate account authentication from vault encryption. Target client-side authenticated encryption with unique nonces and versioned, reviewed key derivation; server stores ciphertext only. Do not claim zero-knowledge without validating the entire design. Hash login passwords with a slow, salted password hash; do not use that hash as a vault encryption key.
- Reuse existing TOTP MFA for authenticator-app enrollment and verification. Email codes are helpful for sign-in/email verification but are not a substitute for an independent possession factor. Add step-up checks to sensitive actions as needed. Rate-limit login, code checks, vault unlock attempts, and recovery flows; do not log code or secret contents.
- Keep private data out of tests, prompts, AI logs, Notion, and analytics. Secrets only through secure environment configuration or a secret store; never ship example/fallback secrets in production.

## Backend engineering and release workflow

1. Read current module code, migrations, tests, `docs/architecture.md`, `docs/database.md`, `docs/coding-standards.md`, and `docs/security-api-state.md`. Define acceptance criteria and a narrow plan.
2. Implement controller -> service -> repository with authorization and centralized errors. Use **Flyway** migrations as the schema authority; JPA `ddl-auto=validate`. Never allow schema auto-update or ad-hoc production SQL.
3. Test success, malformed requests, access denial/other-user IDs, MFA and token lifecycle, duplicate requests, and migration behavior. Use the Gradle wrapper (e.g. `./gradlew :security-api:test` and `./gradlew clean build`). Report tests actually run.
4. Preserve backward-compatible web auth contracts, keep per-environment secrets separate, and coordinate cross-repository changes with an explicit frontend test contract.
5. Deployment choice remains open: evaluate managed container hosting vs small Linux VPS for cost/latency/operational practice. Build Docker/CI, health checks, HTTPS, secret rotation, logging without PII, backups/restore drills, and rollback before production usage.
6. Respect the Git rules above. Use reviewable feature branches and PRs; do not modify or deploy unrelated work.

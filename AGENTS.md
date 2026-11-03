# H-Space API — agent instructions

Shared instructions for every coding agent (Codex reads this file directly;
Claude Code imports it from `CLAUDE.md`). Tool-specific settings live in
`.codex/` and `.claude/`; this file is the source of truth for the rules.

Multi-module Gradle/Spring Boot monorepo. Group `com.henheang`, Java 17,
Spring Boot 3.5.x, PostgreSQL, Flyway.

## Read before changing

| Touching | Read first |
|---|---|
| anything | `docs/architecture.md`, `docs/coding-standards.md` |
| auth / `security-api` | `docs/security-api-state.md` (current code beats the doc — check both) |
| schema, entities, migrations | `docs/database.md` |
| H-Vault (`hub-api/**/vault/**`) | `docs/vault-design.md`, `.claude/rules/vault.md` |
| moving data off Supabase | `docs/spring-migration-plan.md` |
| finance tables | `docs/finance-api.md` |
| build, Docker, deploy | `docs/build-and-run.md`, `docs/deploy-vps.md` |

`.claude/rules/*.md` and `.claude/commands/*.md` are plain Markdown checklists
any agent can follow (e.g. `.claude/commands/migration.md` for a schema change).

## Product

H-Space is a private-by-default daily personal hub. This API is its security
and backend foundation: My Apps, goals/tasks, reminders, finance (Luyra),
Hengo Learning, and the H-Vault credential vault. Aim for a useful daily app
and disciplined Java/Spring Boot/PostgreSQL/DevOps practice, not
feature-count-driven demos. Cross-repo context:
[H-Space web](https://github.com/Hen-Heang/h-space),
[Notion hub](https://app.notion.com/p/3b9acd8b74508151ac22ecff24718832).

## Data ownership and the Supabase migration

- Existing H-Space web feature records still live in Supabase under RLS and
  are not owned by this API. `open-api` todo endpoints are a **sample**, not a
  verified frontend integration.
- Before moving goals, tasks, reminders, finances or vault records to Spring,
  document who owns the data, the API contract, UUID/user mapping, the
  RLS/authorization replacement, and the reconciliation and rollback plan —
  then implement a small tested vertical slice first.
- Notion is for decisions and planning only — never runtime data or secrets.

## Architecture

- One-way module dependency: `api-core` → `security-api` → `open-api` /
  `finance-api` / `hub-api`. The three downstream modules are siblings. Never
  make `api-core` or `security-api` depend on something downstream — stop and
  flag it instead.
- Do not reintroduce the deleted `legacy/spring-jwt-auth` project.
- Shared dependency versions live once in the root `build.gradle`
  `subprojects {}`, not per module.
- Package layout per module: `config/ controller/ domain/ repository/
  security/ service/(+impl/) payload/ exception/`.
- Controller → service → repository, with authorization in the service path.

## Coding

- Lombok for boilerplate; MapStruct for DTO ↔ entity mapping.
- Every response is wrapped in `api-core`'s `ApiResponse`/`ApiStatus`/
  `StatusCode` envelope — no raw DTOs or bare `ResponseEntity<T>` bodies.
- Throw domain/custom exceptions; `GlobalExceptionHandler` is the only place
  that turns them into responses. Don't catch-and-format in controllers.
- Run `./gradlew spotlessApply` rather than hand-formatting.

## Database

- Flyway is the schema authority; `ddl-auto=validate` (only `open-api` and
  `api-core` tests use `create-drop`). Never use schema auto-update or ad-hoc
  production SQL.
- A schema change is a new `V{n}__description.sql`; never edit an applied
  migration. `security-api` → `security-api/src/main/resources/db/migration/`;
  `hub-api` → `hub-api/src/main/resources/db/hub-migration/` (own version
  sequence, history table `flyway_hub_schema_history`).

## Auth (`security-api`)

- Current model: email/username + password, emailed 6-digit sign-in code,
  verified Google ID token, JWT access + rotating refresh tokens, optional TOTP
  MFA with backup codes, RBAC, rate limiting, audit log, invite-only sign-up.
  Read the code and `docs/security-api-state.md` before modifying any of it.
- **Removed — do not resurrect:** the custom OTP controller/service/domain,
  and the cookie-based OAuth2 flow (`HttpCookieOAuth2AuthorizationRequestRepository`,
  `OAuth2*Handler`, `OAuth2UserInfo*`, `CookieUtils`). Flag stale references.
- The Supabase JWT bridge and account import are temporary migration code —
  don't build on them.
- Keep web auth contracts backward-compatible; coordinate cross-repo changes
  with an explicit frontend test contract.

## H-Vault safety gate

- H-Vault holds real credentials in production (since 2026-11-01) and the
  independent security review is **still pending**. Until it's done: no new
  secret fields, sharing, export, or admin access to vault rows; never call it
  "zero-knowledge"; treat review findings as fixes to ship.
- Server stores ciphertext only. Never add server-side decryption or key
  derivation. User id comes only from the JWT principal; every repository
  method is scoped by `userId`. Every vault endpoint requires MFA.
- Never log vault bodies, ciphertext, wrapped keys, codes or secrets.
- Design principles: a real credential vault is never an ordinary CRUD
  schema — it needs a threat model, cross-device recovery model, encryption
  boundaries and independent review. Target client-side authenticated
  encryption with unique nonces and versioned, reviewed key derivation.
- Separate account authentication from vault encryption: login passwords use
  a slow, salted hash, and that hash is never a vault key.
- Reuse the existing TOTP MFA for authenticator enrollment. Email codes help
  with sign-in/verification but are not an independent possession factor.
  Add step-up checks to sensitive actions as needed. Rate-limit login, code
  checks, vault unlock attempts and recovery flows.

## Secrets and private data

- Never read, print or commit `.env`, `deploy/.env`,
  `**/src/main/resources/application-local.yml`, keys or credential files.
  Real values come from environment variables or a secret store only.
- `prod` has no fallback secrets and must stay that way.
- A real-looking secret in a tracked file is an incident (`SECURITY.md`):
  report it so it can be rotated — don't silently delete the line.
- Keep private data out of tests, fixtures, prompts, logs, Notion and
  analytics. Tests use synthetic data only.

## Workflow

1. Read the current module code, migrations, tests and the docs above;
   define acceptance criteria and a narrow plan.
2. Implement controller → service → repository with authorization and
   centralized errors.
3. Test (see Verify), then update the matching `docs/` file.
4. Deployment target is still open (managed containers vs small VPS). Before
   production use of anything new: Docker/CI, health checks, HTTPS, secret
   rotation, logging without PII, backups/restore drills, and rollback.
5. Don't modify or deploy unrelated work.

## Verify

- `./gradlew clean build` (what CI runs) before calling a change done; one
  module: `./gradlew :security-api:test`. On Windows use `gradlew.bat`. Use
  JDK 17–21 (Spotless fails on JDK 25).
- Tests run against real Postgres (`jwt_auth_test`); don't mock persistence
  for anything crossing repository/service boundaries.
- New behavior needs a test: success, malformed input, access denial /
  other-user ids, MFA and token lifecycle, duplicates, migration behavior.
- Report what actually ran. A check you didn't run is not a pass.

## Git

- Never commit or push unless explicitly asked. Never commit or push on
  `main` — use a feature branch and a PR.
- Stage explicit file paths only, never a directory or `git add -A`; the
  owner edits concurrently and unrelated changes must not be swept in.
- No force push, `reset --hard`, `clean`, `rebase`, amend, or branch deletion
  without explicit confirmation — and never as a way past a failing hook.
- One concern per commit, with the "why" in the message.

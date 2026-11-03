# .claude directory

Local Claude Code configuration for this repo, separate from the
project-level `/CLAUDE.md` (which documents the codebase itself for any
contributor/agent).

- `rules/` — narrow, topic-specific rule files that supplement the root
  `CLAUDE.md`, each scoped to one concern and cross-referencing the matching
  `docs/` file for full detail:
  - `architecture.md` — module dependency direction, package boundaries.
  - `coding-style.md` — Lombok/MapStruct, `ApiResponse` envelope, exception
    handling, package layout.
  - `security.md` — secrets handling, `security-api`'s current auth model
    (what's in vs. removed), temporary Supabase code.
  - `vault.md` — H-Vault (`hub-api`) safety gate: ciphertext only, MFA,
    no widening before the security review, no logging of vault content.
  - `testing.md` — JUnit 5, `./gradlew build` vs. `test`, CI parity.
  - `git.md` — commit rules, mid-refactor `main` caution, destructive ops.
  - `review.md` — pre-review checklist, `/code-review` skill usage.
- `commands/` — reusable project commands for recurring task types, each with
  a fill-in-the-blank body that bakes in the relevant `.claude/rules/*`
  constraints:
  - `new-feature.md`, `bug-fix.md`, `refactor.md` — the three general
    change shapes.
  - `api.md` — adding/changing a REST endpoint specifically.
  - `migration.md` — JPA entity/schema changes via a new Flyway migration
    file (see `docs/database.md` — ddl-auto=validate, Flyway is the schema
    authority).
  - `review.md` — requesting a review against the same checklist as
    `.claude/rules/review.md`.
  - `documentation.md` — which `docs/*` files to check when code changes.
- `context/` — supplementary background material that's useful for an agent
  working in this repo but is more narrative/investigative than the
  user-facing `docs/`:
  - `project.md` — what H-Space API is, where it stands right now, deliberate
    rough edges not to "fix" opportunistically.
  - `modules.md` — per-module package breakdown beyond
    `docs/architecture.md`'s summary.
  - `database.md` — Flyway as schema authority (security-api and hub-api
    migration sets), `ddl-auto` per profile (`validate` everywhere except
    `open-api`/`api-core` tests).
  - `api-style.md` — how the `ApiResponse`/`StatusCode`/`ExitCode` envelope
    actually behaves in code, including known quirks (hardcoded status
    codes in `BaseController`/`GlobalExceptionHandler`).
  - `roadmap.md` — condensed `ROADMAP.md`: done vs. deliberately deferred
    vs. out of scope.
  - `tech-stack.md` — dependency versions pulled from the root
    `build.gradle` — re-verify before relying on a version number from here.
- `settings.json` — shared, conservative permissions for builds and read-only Git inspection, plus denials for secrets (any `.env`, local config — via Read and via shell) and destructive Git/file operations. Wires the hooks below.
- `hooks/` — Node guard scripts run by Claude Code (`PreToolUse`):
  - `guard-git.js` — blocks `git commit`/`git push` while on `main`
    (use a feature branch + PR).
  - `guard-secrets.js` — blocks Write/Edit of content that looks like a
    real secret (private keys, JWT/Supabase/GitHub/AWS tokens, non-empty
    `*_SECRET=`/`*_KEY=`/`*_PASSWORD=` assignments outside `*.example`).
- `settings.local.json` — ignored machine-local overrides. Keep credentials and one-off command approvals out of this repository.

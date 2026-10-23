# Development Workflow

## Setup

1. JDK 17, PostgreSQL running locally (db `jwt_auth`, see `docs/database.md`).
2. Copy `.env.example` → `.env` (git-ignored) and fill in local values, or use
   an `application-local.yml` override (also git-ignored).
3. Build once to pull dependencies: `./gradlew build`.

## Day-to-day loop

```bash
./gradlew :security-api:bootRun    # port 8080
./gradlew :open-api:bootRun         # port 8082, needs security-api's JWT
```

- Keep the module dependency direction intact: `common-api` → `security-api`
  → `open-api` (never introduce a cycle — see `docs/architecture.md`).
- Put new code in the matching package (`config/ controller/ domain/
  repository/ security/ service/(+impl/) payload/ exception/`) rather than
  inventing new top-level packages (`docs/coding-standards.md`).
- `security-api` is mid-refactor — check `docs/security-api-state.md` before
  assuming a class (OTP, old cookie-based OAuth2) still exists.

## Before opening a PR

```bash
./gradlew spotlessApply   # auto-format
./gradlew clean build     # compile, test, checkstyle/pmd reports, jacoco
```

`checkstyleMain`/`pmdMain` are advisory only right now — they produce reports
under `*/build/reports/{checkstyle,pmd}/main.html` but don't fail the build.

See also: `code-review-workflow.md`, `testing-workflow.md`, `git-workflow.md`,
and the root `CONTRIBUTING.md`.

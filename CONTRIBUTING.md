# Contributing to AuthHub

## Setup

See the [README](README.md#prerequisites) for prerequisites (JDK 17,
PostgreSQL) and [Configuration profiles](README.md#configuration-profiles) for
environment variables. Copy `.env.example` and fill in your own values before
running anything.

## Workflow

1. Keep the module dependency direction intact: `common-api` ← `security-api`
   ← `open-api`. Never introduce a cycle (see `docs/architecture.md`).
2. Follow `docs/coding-standards.md` — Lombok for boilerplate, MapStruct for
   DTO↔entity mapping, `ApiResponse`/`ApiStatus`/`StatusCode` envelopes for all
   API responses, exceptions thrown as domain exceptions (not caught/formatted
   in controllers).
3. Put new code in the matching package (`config/ controller/ domain/
   repository/ security/ service/(+impl/) payload/ exception/`) rather than
   inventing new top-level packages.
4. Before committing:
   ```bash
   ./gradlew spotlessApply   # auto-format
   ./gradlew build           # compile, test, checkstyle/pmd reports, jacoco
   ```
   `checkstyleMain`/`pmdMain` are advisory (reports only) for now — see
   `*/build/reports/checkstyle/main.html` and `*/build/reports/pmd/main.html`
   if you want to check your changes against them, but they won't fail the
   build.
5. Never commit real secrets. `application-local.yml` files are git-ignored —
   put your local overrides there or in a `.env` (also git-ignored).

## Commits & PRs

- Keep commits logically scoped (one concern per commit) with a clear "why"
  in the message, not just "what".
- `main` is mid-refactor (OTP/OAuth2 removal, MFA/audit/rate-limiting
  addition per `docs/security-api-state.md`) — check `git status`/`git log`
  before staging so you don't sweep unrelated in-progress work into your
  commit.
- Run `./gradlew clean build` before opening a PR; CI (`.github/workflows/ci.yml`)
  runs the same command against a real Postgres service container.

## Reporting bugs / proposing features

Open a GitHub issue. For security issues, see [SECURITY.md](SECURITY.md)
instead.

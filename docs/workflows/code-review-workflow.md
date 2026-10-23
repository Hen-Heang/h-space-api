# Code Review Workflow

## Before requesting review

- `./gradlew clean build` passes locally (same command CI runs).
- `./gradlew spotlessApply` has been run so formatting isn't part of the diff.
- Commits are logically scoped (one concern per commit) with a "why" in the
  message, not just a "what".
- If touching `security-api`, confirm you haven't reintroduced anything listed
  as removed in `docs/security-api-state.md` (OTP, old cookie-based OAuth2).

## Self-review / assisted review

Use the `/code-review` skill for a diff-level pass before pushing:

- `/code-review` (default effort) for a normal-sized change.
- `/code-review high` or `/code-review max` for larger or riskier diffs.
- `/code-review ultra` (or the `/ultrareview` alias) for a deep multi-agent
  cloud review of the whole branch, or `/code-review ultra <PR#>` for an
  already-open PR. This is user-triggered and billed — it's not run
  automatically.

## What reviewers check

- Module dependency direction (`docs/architecture.md`) isn't violated.
- `ApiResponse`/`ApiStatus`/`StatusCode` envelope used for all responses —
  no raw DTOs or bare `ResponseEntity<T>` bodies (`docs/coding-standards.md`).
- Exceptions are domain/custom exceptions handled by `GlobalExceptionHandler`,
  not caught-and-formatted in controllers.
- No real secrets committed (`jwt.secret`, DB creds) — see `docs/database.md`
  and `SECURITY.md`.
- Tests exist for new behavior (see `testing-workflow.md`).

## Merging

- CI (`.github/workflows/ci.yml`) must be green — it runs `./gradlew clean
  build` against a real Postgres 16 service container with
  `SPRING_PROFILES_ACTIVE=test`.
- Squash or keep commits as appropriate for the change; don't sweep unrelated
  in-progress work (the `main` refactor — see `docs/security-api-state.md`)
  into the same commit/PR.

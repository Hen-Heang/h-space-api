# Review Rule

Full detail: `docs/workflows/code-review-workflow.md`.

Before treating a change as ready for review:

- `./gradlew clean build` passes locally (same command CI runs).
- `./gradlew spotlessApply` has been run.
- Module dependency direction (`.claude/rules/architecture.md`) isn't
  violated.
- `ApiResponse`/`ApiStatus`/`StatusCode` envelope used for all responses —
  no raw DTOs or bare `ResponseEntity<T>` bodies
  (`.claude/rules/coding-style.md`).
- Exceptions are domain/custom exceptions handled by
  `GlobalExceptionHandler`, not caught-and-formatted in controllers.
- No real secrets committed (`.claude/rules/security.md`).
- New behavior has a test (`.claude/rules/testing.md`).
- If touching `security-api`, nothing removed per
  `docs/security-api-state.md` (custom OTP, old cookie-based OAuth2) has been
  reintroduced.

For an assisted pass, use the `/code-review` skill (`/code-review high` or
`max` for larger diffs; `/code-review ultra` for a deep multi-agent cloud
review — user-triggered and billed, never launch it automatically).

# Testing Rule

Full detail: `docs/build-and-run.md`, `docs/workflows/testing-workflow.md`.

- JUnit 5 across all modules.
- `./gradlew build` before considering any change done — don't just eyeball
  the diff. `./gradlew test` alone skips checkstyle/pmd/jacoco reporting, so
  prefer `build` (or `clean build`) to match what CI runs.
- CI (`.github/workflows/ci.yml`) runs `./gradlew clean build` against a real
  `postgres:16` service container with `SPRING_PROFILES_ACTIVE=test` —
  `application-test.yml` ships fixed, non-sensitive defaults, so no
  `JWT_SECRET` is needed for tests.
- New behavior needs a test; don't ship a change that only "looks right" by
  inspection when a test could confirm it.
- Prefer exercising the real Postgres test profile over mocking the database
  for anything that crosses repository/service boundaries — mocked
  persistence can pass while the real integration is broken.

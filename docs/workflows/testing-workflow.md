# Testing Workflow

JUnit 5 across all modules.

```bash
./gradlew test                     # all modules
./gradlew :security-api:test       # one module
./gradlew build                    # compile + test + checkstyle/pmd + jacoco
./gradlew build -x test            # skip tests (compile only)
```

## CI parity

CI (`.github/workflows/ci.yml`) runs `./gradlew clean build` with:

- A real `postgres:16` service container (`jwt_auth_test` db, `postgres`/`123`).
- `SPRING_PROFILES_ACTIVE=test`, which picks up `application-test.yml`'s
  fixed, non-sensitive defaults — no `JWT_SECRET` env var needed for tests.

Run `./gradlew clean build` locally before pushing so you hit the same path
CI does, rather than relying on `./gradlew test` alone (which skips
checkstyle/pmd/jacoco reporting).

## Coverage / reports

After a build, reports land under:

```
*/build/reports/tests/tests/index.html
*/build/reports/checkstyle/main.html
*/build/reports/pmd/main.html
*/build/reports/jacoco/
```

CI uploads these as the `test-reports` artifact on every run.

## Guidance

- New behavior needs a test — this is checked in `code-review-workflow.md`.
- Don't mock what CI already gives you for free: prefer hitting the real
  Postgres service/test profile over mocking the database for anything that
  exercises repository/service integration.

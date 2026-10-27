# Build & Run

```bash
./gradlew build                    # build all modules, run tests
./gradlew :security-api:build      # build one module
./gradlew build -x test            # skip tests

./gradlew :security-api:bootRun    # port 8080
./gradlew :open-api:bootRun         # port 8082, needs security-api's JWT to authenticate

./gradlew test                     # all tests, JUnit 5
./gradlew :security-api:test       # one module
```

Run `./gradlew build` before considering any change done — don't just eyeball the diff.

## Docker

```bash
cp .env.example .env               # then fill in JWT_SECRET, MFA_ENCRYPTION_KEY, ...
docker compose up --build          # Postgres (127.0.0.1:5433) + security-api (127.0.0.1:8080)
curl localhost:8080/actuator/health   # {"status":"UP"} once Flyway has migrated
docker compose down                # keep data; add -v to drop the postgres-data volume
```

- The image (`Dockerfile`) packages `security-api` only; tests are not run
  during `docker build` — CI's `./gradlew clean build` is the gate.
- Compose runs the `dev` profile (override with `COMPOSE_SPRING_PROFILE`);
  `local` can't work in a container because `application-local.yml` is
  git-ignored and excluded by `.dockerignore`.
- `/actuator/health` (plus `/liveness`, `/readiness`) is the only exposed
  actuator endpoint, public and detail-free; it checks the DB, not SMTP.
- A key set but left empty in `.env` (e.g. `JWT_SECRET=`) is passed as an
  empty string and beats the profile's fallback default — delete the line or
  fill it in.

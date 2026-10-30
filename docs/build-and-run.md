# Build & Run

```bash
./gradlew build                    # build all modules, run tests
./gradlew :security-api:build      # build one module
./gradlew build -x test            # skip tests

./gradlew :security-api:bootRun    # port 8080
./gradlew :open-api:bootRun         # port 8082, needs security-api's JWT to authenticate
./gradlew :hspace-api:bootRun       # port 8084, H-Space product API (same JWTs, same DB as security-api)

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

### Published images (CI)

CI's `image` job (`.github/workflows/ci.yml`) builds the same `Dockerfile`
after `build` passes and boots it against a throwaway Postgres, expecting
`/actuator/health` to be `UP`:

- **Pull requests** — build + smoke check only; nothing is pushed.
- **Push to `main`** — pushed to `ghcr.io/hen-heang/h-space-api/security-api`
  as `latest` and `sha-<full commit>`, then the smoke check runs against the
  pushed digest.
- **Release tag `vX.Y.Z`** — also tagged `vX.Y.Z` and `vX.Y`; `latest` only
  moves on `main`.

Each publish run's summary prints the verified `image@sha256:...` reference.
Deploy by that digest rather than a tag — tags can be moved, a digest can't.

```bash
docker pull ghcr.io/hen-heang/h-space-api/security-api:latest
```

New GHCR packages start **private**: pulling from a server needs a
`docker login ghcr.io` with a token that has `read:packages`, or change the
package's visibility in GitHub (Profile → Packages → security-api).

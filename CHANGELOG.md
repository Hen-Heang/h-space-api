# Changelog

All notable changes to this project are documented here. Format loosely
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this
project has no released versions yet, so everything lives under
`[Unreleased]`.

## [Unreleased]

### Added
- `api-core` can be published as `com.henheang:api-core`
  (`./gradlew :api-core:publishToMavenLocal`) for reuse by other APIs.
- `local` / `dev` / `test` / `prod` Spring configuration profiles for
  `common-api`, `security-api`, and `todoapi`.
- Spotless (Google Java Format), Checkstyle, PMD, and JaCoCo, wired into
  every module via the root `build.gradle`.
- GitHub Actions CI (`.github/workflows/ci.yml`): full `./gradlew clean build`
  against a real Postgres service container on every push/PR to `main`.
- `.editorconfig`, `.env.example`, `ROADMAP.md`, `SECURITY.md`,
  `CONTRIBUTING.md`, `LICENSE` (MIT), this `CHANGELOG.md`.
- Docker support for `security-api`: a multi-stage `Dockerfile` (non-root
  JRE 17 runtime with a built-in healthcheck) and `compose.yaml` that runs
  it with PostgreSQL 16 via `docker compose up --build` — see
  `docs/build-and-run.md`.
- Public `/actuator/health` (plus `/liveness` and `/readiness`) in
  `security-api` for container healthchecks; it checks the database, hides
  details, and no other actuator endpoint is exposed.
- CI `image` job: builds the `security-api` image and smoke-tests it against
  Postgres on every PR; on `main` and `vX.Y.Z` tags it publishes to GHCR
  (`latest`, `sha-<commit>`, version tags) and records the verified digest.

### Changed
- Renamed the shared library module `common-api` to `api-core` and its
  package `com.henheang.commonapi` to `com.henheang.apicore`.
- Deduplicated per-module `build.gradle` files — group/version/plugins/
  repositories/test dependencies now live once in the root `build.gradle`;
  shared dependency versions (mapstruct, springdoc, jjwt) centralized via an
  `ext` block instead of being repeated as literals in three places.
- Unified module versions to `0.0.1-SNAPSHOT` (previously `common-api`/
  `security-api` were `1.0-SNAPSHOT` while `todoapi`/root were
  `0.0.1-SNAPSHOT`).
- `README.md` rewritten to document profiles, secrets handling, and the new
  quality tooling.

### Fixed
- `gradlew` was committed without its executable bit, so `./gradlew` failed
  with "permission denied" on Linux/macOS checkouts, including CI.
- `todoapi`'s test class was incorrectly annotated `@SpringBootApplication`
  in addition to the real `TodoapiApplication`, causing Spring Boot to find
  two boot configuration classes and fail context loading in tests.
- `todoapi`'s `OpenApiConfig` and `security-api`'s `OpenApiConfig` collided
  as Spring bean names (`openApiConfig`) once `todoapi` component-scans
  `security-api`'s package — gave `todoapi`'s config bean an explicit,
  distinct name.

### Security
- Removed hard-coded JWT secrets and database passwords from
  `common-api`, `security-api`, and `todoapi`'s `application*.yml` files;
  all secrets now come from environment variables with no insecure
  fallback defaults outside the `local`/`test` profiles.
- `security-api/src/main/resources/application-local.yml` (previously
  committed with a real-looking secret) is no longer tracked by git. See
  [SECURITY.md](SECURITY.md) for the historical-exposure note and rotation
  guidance.
- Rate limiting and audit/login-history IPs no longer trust the first
  `X-Forwarded-For` entry, which the caller controls — sending a new value
  per request bypassed the per-IP limit on login, MFA and email-code
  endpoints and forged the logged IP. The client IP now comes from Tomcat's
  `RemoteIpValve` (`server.forward-headers-strategy: native`), which only
  honours the header from a trusted (private/loopback) proxy.

# Tech Stack Context

Versions as of the current `build.gradle` — re-check if this drifts, this
is exactly the kind of file that goes stale silently.

## Core

- Java 17 (`sourceCompatibility`/`targetCompatibility`), Gradle (wrapper
  included, no separate install needed).
- Spring Boot `3.5.15`, Spring Dependency Management plugin `1.1.7`.
- Group `com.henheang`, all modules `0.0.1-SNAPSHOT` (single version, no
  releases yet — see `.claude/context/roadmap.md`).
- PostgreSQL (`org.postgresql:postgresql`, runtime only).

## Shared library versions (root `build.gradle`'s `ext {}`)

- MapStruct `1.5.5.Final`
- springdoc-openapi `2.8.6` (Swagger UI)
- jjwt (`io.jsonwebtoken`) `0.11.5`

## Quality tooling (every module, via `subprojects {}`)

- Spotless `7.0.2` — Google Java Format `1.25.2` (AOSP variant), unused
  import removal, trailing whitespace trim. Run `./gradlew spotlessApply`.
- Checkstyle `10.20.2` — advisory only (`ignoreFailures = true`), config at
  `config/checkstyle/checkstyle.xml`.
- PMD `7.9.0` — advisory only, ruleset at `config/pmd/ruleset.xml`.
- JaCoCo `0.8.12` — reports generated on every test run, no coverage gate
  enforced yet.

## `security-api`-specific dependencies

- Flyway (`flyway-core` + `flyway-database-postgresql`) — schema authority,
  see `.claude/context/database.md`.
- Spring Security, `spring-boot-starter-oauth2-resource-server` +
  `-oauth2-client`, `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`.
- `libphonenumber` `8.13.11` — phone number validation.
- `bucket4j_jdk17-core` `8.14.0` — rate limiting.
- `dev.samstevens.totp` `1.7.1` + `zxing` `3.5.3` (core + javase) — TOTP MFA
  and its QR code generation.
- `google-api-client` `2.8.0` — Google ID-token verification
  (`GoogleTokenVerifier`).
- `spring-boot-starter-cache` + Caffeine `3.1.8` — permission cache
  (`CacheConfig`/`PermissionQueryService`).
- `commons-lang3` `3.12.0`, `commons-beanutils` `1.9.4`.

## `open-api`-specific dependencies

- Spring Security + jjwt (validates tokens issued by `security-api`, doesn't
  issue its own).
- springdoc-openapi (Swagger UI).

## CI

GitHub Actions (`.github/workflows/ci.yml`): JDK 17 (temurin), Gradle setup
action, `postgres:16` service container, `./gradlew clean build`, uploads
test/checkstyle/pmd/jacoco reports as an artifact.

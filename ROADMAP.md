# Roadmap

Informal, living list — not a commitment or timeline. See `docs/` for the
current state of each module before assuming anything below is already done.

## Done (foundation)

- [x] Multi-module Gradle build with a clean one-way dependency direction
      (`common-api` → `security-api` → `open-api`), deduplicated per-module
      `build.gradle` files.
- [x] `local` / `dev` / `test` / `prod` Spring profiles per runnable module.
- [x] Secrets externalized to environment variables; no committed real
      secrets going forward (see [SECURITY.md](SECURITY.md) for the one
      historical exception).
- [x] Spotless (Google Java Format), Checkstyle, PMD, JaCoCo wired into every
      module; GitHub Actions CI running the full build against a real
      Postgres service container.
- [x] `.editorconfig`, `.env.example`, and standard project docs.

## Known follow-ups (not yet done, deliberately deferred)

- **Tighten Checkstyle/PMD** — currently advisory (`ignoreFailures = true`)
  so the existing codebase doesn't fail `./gradlew build`. Once the reports
  are clean (or the ruleset is trimmed to what the team actually wants
  enforced), flip `ignoreFailures` off.
- **JaCoCo coverage gate** — reports are generated but there's no minimum
  coverage threshold enforced yet.
- **`common-api`'s `application.yml`** — this library module carries a full
  Spring Boot app config (datasource, mail, JWT) purely so its
  `@SpringBootTest` context test can boot; worth revisiting whether that test
  needs the full context at all.
- **Version catalog** — shared dependency versions currently live in an `ext`
  block in the root `build.gradle`; migrating to `gradle/libs.versions.toml`
  would be a small further cleanup.

## Feature work (explicitly out of scope until the foundation work above is
reviewed — see `CLAUDE.md`)

- OTP/OAuth2 removal and MFA/audit/rate-limiting completion — in progress on
  `main` per `docs/security-api-state.md`. Do not resurrect removed OTP/OAuth2
  classes.
- Everything else in `security-api`'s and `open-api`'s feature backlog.

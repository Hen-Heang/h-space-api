# Roadmap Context

Full list: `ROADMAP.md` (informal, living, not a commitment/timeline).

## Done (foundation)

- Multi-module Gradle build, clean one-way dependency direction, deduplicated
  per-module `build.gradle` files.
- `local`/`dev`/`test`/`prod` Spring profiles per runnable module.
- Secrets externalized to env vars (one historical exception, see
  `SECURITY.md`).
- Spotless, Checkstyle, PMD, JaCoCo wired into every module; CI running full
  build against a real Postgres container.
- `.editorconfig`, `.env.example`, standard project docs.

## Deliberately deferred (don't "fix" opportunistically)

- Checkstyle/PMD tightening (`ignoreFailures = true` currently) — deferred
  until reports are clean or the ruleset is trimmed.
- JaCoCo coverage gate — reports generated, no minimum enforced yet.
- `api-core`'s `application.yml` carrying a full Spring Boot config just to
  let its `@SpringBootTest` context test boot — worth revisiting.
- Version catalog migration (`ext` block in root `build.gradle` →
  `gradle/libs.versions.toml`).

## Explicitly out of scope right now

- Widening H-Vault (new secret fields, sharing, export, admin access) before
  the independent security review (`docs/vault-design.md` §9).
- Moving a Supabase domain to Spring without the ownership/contract/
  rollback plan in `docs/spring-migration-plan.md`.
- Resurrecting removed OTP/OAuth2 classes, even if a roadmap item seems to
  imply reviving them (`docs/security-api-state.md`).

## How to use this

Before proposing a "nice to have" cleanup, check this list — if it's here,
it's a known, deliberate gap, not an oversight. Don't silently fix a deferred
item as a drive-by inside an unrelated change; call it out and let the user
decide whether to pull it in.

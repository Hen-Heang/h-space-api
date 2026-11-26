# Architecture Rule

Full detail: `docs/architecture.md`.

- Dependency direction is one-way and must never cycle:
  `api-core` → `security-api` → `hub-api`.
- `engineering-api` (8085, owner-only Engineering Hub) depends on `api-core`
  only and verifies Supabase tokens itself — never add a `security-api`
  dependency or scan to it.
- `api-core` has no bootJar and isn't runnable — it's the shared library
  (`ApiResponse`/`ApiStatus`/`StatusCode`, pagination, enum converters,
  interceptors). Everything else depends on it, never the reverse.
- `hub-api` (8084 with bootRun, 8080 in its image) is the **one deployed
  H-Space app**: it embeds `security-api` (auth) and holds the product
  areas — inbox and H-Vault (`com.henheang.hubapi`) and finance
  (`com.henheang.financeapi`). A new area is a new package set in `hub-api`,
  not a new runnable module. `security-api` (8080) runs alone only for its
  tests and `bootRun`; it may never depend on `hub-api`.
- Auth settings live once in `security-api.yml` /
  `security-api-{profile}.yml`, imported by both apps — never copy them into
  `hub-api`'s config.
- The archived `legacy/spring-jwt-auth/` project has been deleted — don't
  reintroduce it (it's in git history if ever needed for reference).
- Shared dependency versions/plugin config live once in the root
  `build.gradle`'s `subprojects {}` block — don't repeat them per-module.
- Before adding a new module or cross-module call, check this direction
  holds; if a change would require `api-core` or `security-api` to depend
  on something downstream, stop and flag it rather than introducing the
  cycle.

# Architecture Rule

Full detail: `docs/architecture.md`.

- Dependency direction is one-way and must never cycle:
  `api-core` → `security-api` → `open-api` / `finance-api` / `hub-api`
  (the three downstream modules are siblings; none depends on another).
- `api-core` has no bootJar and isn't runnable — it's the shared library
  (`ApiResponse`/`ApiStatus`/`StatusCode`, pagination, enum converters,
  interceptors). Everything else depends on it, never the reverse.
- `security-api` (port 8080) is the auth service. `open-api` (8082, sample),
  `finance-api` (8083) and `hub-api` (8084, personal-hub data incl. H-Vault)
  are secured by `security-api`'s JWT — they may depend on `security-api`,
  not vice versa.
- The archived `legacy/spring-jwt-auth/` project has been deleted — don't
  reintroduce it (it's in git history if ever needed for reference).
- Shared dependency versions/plugin config live once in the root
  `build.gradle`'s `subprojects {}` block — don't repeat them per-module.
- Before adding a new module or cross-module call, check this direction
  holds; if a change would require `api-core` or `security-api` to depend
  on something downstream, stop and flag it rather than introducing the
  cycle.

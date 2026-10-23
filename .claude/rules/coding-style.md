# Coding Style Rule

Full detail: `docs/coding-standards.md`.

- Use Lombok for boilerplate (getters/builders/constructors) — don't
  hand-write what Lombok annotations already generate.
- Use MapStruct for DTO ↔ entity mapping in `common-api` and `security-api`
  — don't hand-write mappers.
- Every API response is wrapped in `common-api`'s
  `ApiResponse`/`ApiStatus`/`StatusCode` envelope. Never return a raw DTO or
  a bare `ResponseEntity<T>` body.
- Throw domain/custom exceptions from services and controllers; don't
  catch-and-format errors locally. `security-api`'s `GlobalExceptionHandler`
  is the single place translating exceptions to responses.
- Package layout per module is fixed: `config/ controller/ domain/
  repository/ security/ service/(+impl/) payload/ exception/`. Put new code
  in the matching package rather than inventing new top-level packages.
- Run `./gradlew spotlessApply` before considering formatting done — don't
  hand-format to match Google Java Format.

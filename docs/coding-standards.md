# Coding Standards

- **Lombok** everywhere for boilerplate (getters/builders/constructors) — don't hand-write them.
- **MapStruct** for DTO <-> entity mapping in `common-api` and `security-api`.
- All API responses wrapped in `common-api`'s `ApiResponse`/`ApiStatus`/`StatusCode` — don't return raw DTOs or `ResponseEntity<T>` bodies without the envelope.
- Exceptions are translated centrally by `security-api`'s `GlobalExceptionHandler` — throw domain/custom exceptions, don't catch-and-format in controllers.
- Package layout per module: `config/ controller/ domain/ repository/ security/ service/(+impl/) payload/ exception/`. Put new code in the matching package rather than inventing new top-level ones.

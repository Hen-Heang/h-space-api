# API Endpoint Prompt

Use for adding or changing a REST endpoint.

```
{{Add|Change}} endpoint {{METHOD /path}} in {{module}}'s {{XController}}.

Request/response:
- Request: {{payload shape, validation rules}}
- Response: {{success shape}} wrapped in ApiResponse<{{T}}> with
  StatusCode.{{...}}
- Errors: {{expected failure cases}} → thrown as {{domain exception}},
  handled by GlobalExceptionHandler (don't add local try/catch formatting).

Layers to touch:
- controller/ — thin, delegates to service.
- service/(+impl/) — business logic.
- payload/ — request/response DTOs (MapStruct mapping to/from domain/entity).
- repository/ — only if new persistence access is needed.

Constraints:
- No raw DTOs or bare ResponseEntity<T> — always the ApiResponse envelope.
- Auth: confirm whether the endpoint needs to be public
  (docs/security-api-state.md's PublicController pattern) or authenticated,
  and wire security config accordingly — don't accidentally leave a
  sensitive endpoint public or a public one behind auth.
- Update Swagger/OpenAPI annotations so springdoc UI reflects the change.

Deliverables:
- Controller/service/payload tests (happy path + validation/error cases).
- ./gradlew clean build passing.
- CHANGELOG.md [Unreleased] entry.
```

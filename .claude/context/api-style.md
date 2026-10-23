# API Response Style — How It Actually Works

Rule-level summary: `.claude/rules/coding-style.md`. This file documents the
actual envelope mechanics in `common-api`/`security-api`, including quirks
worth knowing before adding a new endpoint.

## The envelope classes (`common-api`, `components/common/api/`)

- `ApiResponse<T>` — fields `statusCode` (an `ApiStatus`, serialized as JSON
  key `"status"`), `data` (generic `T`), `common` (`Common`, omitted from
  JSON if null). Built via `ApiResponse.builder().status(...).data(...).build()`
  — there are no `.success()`/`.error()` static factories, despite what you
  might expect.
- `ApiStatus` — just `{ code, message }`, constructible from a `StatusCode`
  enum constant.
- `StatusCode` — enum of `(code, message, httpCode)` triples: `SUCCESS`,
  `UNAUTHORIZED`, `FORBIDDEN`, `BAD_REQUEST` (+ ~19 domain 400xx variants),
  `NOT_FOUND` (+ ~12 404xx variants), `INTERNAL_SERVER_ERROR` (+2 more),
  `TOO_MANY_REQUESTS`, `SEND_OTP_FAILED`. A static initializer throws
  `IllegalStateException` at class-load if two constants share a `code` —
  adding a new value with a duplicate code breaks the whole app at startup,
  not just that one path.
- `Common` — envelope metadata (`api_id`/`request_id`/`device_id`) built
  from request headers (`x-api-id`/`x-request-id`/`x-device-id`).
- `EmptyJsonResponse` — a `record` used as `data` for endpoints with no real
  payload (e.g. logout).

## A second, parallel status enum: `ExitCode`

`common-api` also defines `ExitCode` (auth 1000s, registration 1100s,
password reset 1200s, OAuth 1300s, email verification 1400s, account
unlock 1500s, system 5000s, 9999 unknown), with its own `fromCode(int)`
lookup. In practice `security-api`'s domain/auth exceptions
(`BusinessException`, `AuthException`) carry `ExitCode`, while `StatusCode`
is what `ApiResponse.builder()` / `BaseController` use directly. **The two
enums coexist and are not interchangeable** — don't assume a `StatusCode`
value has a matching `ExitCode` or vice versa.

## Building a response — the actual pattern (`BaseController`)

```java
public <T> ResponseEntity<ApiResponse<?>> buildResponse(HttpStatus status, T data, HttpHeaders headers) {
    ApiResponse<?> apiResponse = ApiResponse.builder().status(StatusCode.SUCCESS).data(data).build();
    return ResponseEntity.ok().headers(headers).body(apiResponse);
}
public <T> ResponseEntity<ApiResponse<?>> ok(T data) { return buildResponse(HttpStatus.OK, data); }
public <T> ResponseEntity<ApiResponse<?>> ok() { return buildResponse(HttpStatus.OK, new EmptyJsonResponse()); }
```

Controllers just call `ok(data)` / `ok()` (e.g. `AuthController.login`,
`.logout`). **Known quirk**: `buildResponse`'s `status`/`HttpStatus`
parameter is accepted but not actually used — the envelope's `StatusCode` is
hardcoded to `SUCCESS` and the HTTP wrapper is always `ResponseEntity.ok()`
regardless of what's passed. Don't assume passing a different `HttpStatus`
to `ok(...)` changes anything — as of this writing it doesn't. If you need a
non-200/non-SUCCESS success response, that's a gap, not a supported path —
flag it rather than assuming existing controllers demonstrate how to do it.

## Error path (`GlobalExceptionHandler`)

Most `@ExceptionHandler` methods build
`new ApiResponse<>(new ApiStatus(ExitCode.SYSTEM_ERROR.getCode(), ExitCode.SYSTEM_ERROR.getMessage()), ex.getMessage())`
— i.e. the envelope's status is hardcoded to the generic `SYSTEM_ERROR`
regardless of the actual exception type; the real detail only shows up in
`data` (exception message, or a field-error map for validation errors). The
HTTP status on the `ResponseEntity` itself is set correctly per handler
(`NOT_FOUND`/`BAD_REQUEST`/`UNAUTHORIZED`/`FORBIDDEN`/`CONFLICT`/
`INTERNAL_SERVER_ERROR`), so callers can rely on the HTTP status code but
**not** on the envelope's `status.code` matching the HTTP status for most
exception types.

Two exceptions to that pattern actually use the real code:
- `BusinessException` → `new ApiStatus(ex.getErrorCode())` (its carried
  `StatusCode`) and HTTP status from `ex.getErrorCode().getHttpCode()`.
- `AuthException` → still hardcodes the envelope to `SYSTEM_ERROR`, but
  derives HTTP status from a private range-bucketing helper over its
  `ExitCode`.

If you're adding a new failure case and want the envelope's `status` field
to reflect it accurately (not just the HTTP status), throw a
`BusinessException` with the right `StatusCode` — that's the one path that
actually propagates through.

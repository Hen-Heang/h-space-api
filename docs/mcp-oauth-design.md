# H-Space MCP authorization — design (issue #36)

Status: **design approved by the owner (2026-11-09), not implemented.** Decisions are marked **[DECIDED]**; staging checks are marked **[VERIFY]**.
Cross-repo: the MCP endpoint stays in `Hen-Heang/h-space` (`apps/web/app/mcp`);
this repo becomes its authorization server. Related: h-space
`docs/SECOND_BRAIN_ARCHITECTURE.md` §4.1 (Option A), `docs/chatgpt-mcp-integration.md`.

## 1. Problem

`/mcp` (Next.js, `@modelcontextprotocol/server` 2.0.0, stateless Streamable
HTTP) verifies bearer tokens issued by Supabase OAuth
(`lib/mcp/auth.ts` `SupabaseMcpTokenVerifier`). It then passes **that same
token** to Supabase so RLS (`auth.uid()`) scopes every query
(`createUserSupabaseClient`). Browser sign-in moved to `security-api`, so:

- the consent page (`app/(main)/oauth/consent`) calls `supabase.auth.oauth.*`,
  which now throws — no user can obtain an MCP token;
- `security-api` has no client, scope, audience, PKCE or JWKS concept;
- the bridge token (HS256, `aud=authenticated`, no `client_id`) must not stand in.

## 2. What exists in `security-api` today (relevant facts)

| Fact | Where | Consequence for this design |
|---|---|---|
| One HS512 shared-secret JWT, claims `sub`, `jti`, `iat`, `exp`, optional `token_type`; **no `iss`, no `aud`** | `JwtTokenProvider`, `JwtConfig` | MCP tokens must use a **different key and algorithm**, so neither kind of token is accepted where the other belongs (§5.4) |
| `JwtAuthenticationFilter` accepts any validly signed HS512 token without `token_type` | `security/JwtAuthenticationFilter.java` | An ES256 MCP token fails the HS512 check there: no Spring API (incl. hub-api / H-Vault) accepts it. Keep that; add a test |
| Refresh tokens: random 32 bytes, SHA-256 hash stored, rotation via one conditional update (`revokeIfActive`) | `RefreshTokenServiceImpl`, `RefreshTokenRepository` | Reuse the pattern for MCP refresh tokens |
| `createRefreshToken` **revokes all of the user's refresh tokens** (single browser session) | `RefreshTokenServiceImpl:32` | MCP grants need their **own** tables; reusing `refresh_tokens` would sign the browser out on every MCP login |
| Step-up MFA with once-only codes and shared lockout | `MfaStepUpService` | Reuse at consent (§4.3) |
| In-memory per-path rate limiting | `RateLimitingFilter` | Add the new public endpoints to it |
| Audit log | `AuditEventType`, `AuditLogService` | Add OAuth events (§7) |
| Stateless, CSRF off, bearer-only | `WebSecurityConfig` | Consent decision is a bearer-authenticated API call, so it is not CSRF-able |

## 3. Decision: hand-built narrow AS, not Spring Authorization Server

Spring Authorization Server's `/oauth2/authorize` expects a **server-side
session** login on the API origin (form login). H-Space's login (password or
username, email code, Google ID token, TOTP/backup codes, lockout) lives in the
Next.js UI with tokens in the web origin's storage. Bending SAS to that means
hand-crafting its internal `OAuth2Authorization` records from a custom
endpoint, which couples us to SAS internals without much benefit.

Instead: a small, explicit implementation in `security-api` — about 6
endpoints. It follows patterns this module already has and tests (hashed
single-use codes, conditional-update rotation, audit). JWTs are signed with
**Nimbus JOSE** (already on the classpath via
`spring-boot-starter-oauth2-resource-server`). jjwt stays for the browser
tokens. OAuth 2.1 constraints we enforce: auth code + **PKCE S256 only**,
exact `redirect_uri` match, no implicit/password grants, public clients rotate
refresh tokens, `iss` in the authorization response (RFC 9207).

## 4. Flow

```
MCP client ──(1) POST /mcp no token──────────────► Next.js /mcp
           ◄─ 401 WWW-Authenticate: Bearer resource_metadata=".../.well-known/oauth-protected-resource/mcp"
           ──(2) GET protected-resource metadata ─► Next.js  → authorization_servers=[ISSUER]
           ──(3) GET ISSUER/.well-known/oauth-authorization-server ─► security-api
           ──(4) browser → WEB/oauth/authorize?response_type=code&client_id&redirect_uri
                    &code_challenge&code_challenge_method=S256&scope&state&resource
                    Next.js page:
                      a. POST API/api/oauth/authorize/validate   (public) → client name, scopes
                      b. not signed in → existing login (MFA included), return here
                      c. consent screen (+ MFA step-up code)
                      d. POST API/api/oauth/authorize/approve    (Bearer: browser token)
                         → { redirectUri: "<redirect_uri>?code=…&state=…&iss=ISSUER" }
           ◄─(5) redirect with code
           ──(6) POST API/oauth/token (code, code_verifier, redirect_uri, client_id, resource)
           ◄─ { access_token (ES256 JWT, 10 min), refresh_token (opaque, rotating), scope }
           ──(7) POST /mcp  Authorization: Bearer <access_token> ─► Next.js verifies via JWKS
```

### 4.1 Endpoints — `security-api`

| Method & path | Auth | Purpose |
|---|---|---|
| `GET /.well-known/oauth-authorization-server` | public | RFC 8414 metadata (§6) |
| `GET /oauth/jwks` | public | Public signing keys (current + previous `kid`) |
| `POST /api/oauth/authorize/validate` | public, rate-limited | Validates `client_id`, exact `redirect_uri`, `response_type=code`, S256 challenge, scopes ⊆ client's allowed scopes, `resource` = configured MCP resource. Returns display data only. **Invalid client/redirect → error shown on page, never redirected** (open-redirect guard) |
| `POST /api/oauth/authorize/approve` | browser JWT + MFA step-up | Re-validates everything, creates a code, returns the redirect URL |
| `POST /api/oauth/authorize/deny` | browser JWT | Returns redirect with `error=access_denied` |
| `POST /oauth/token` | public client (`client_id`), rate-limited | `authorization_code` and `refresh_token` grants |
| `POST /oauth/revoke` | public client | RFC 7009; refresh token → revoke whole grant. Always 200 |
| `GET /api/oauth/grants` / `DELETE /api/oauth/grants/{id}` | browser JWT | "Connected apps": user lists/revokes **own** grants (scoped by principal `userId`) |
| `GET/POST/PATCH /api/admin/oauth-clients` | `ROLE_ADMIN` | Register, list, disable clients (§4.2) |

**Envelope exception [DECIDED]:** `/oauth/token`, `/oauth/revoke`, `/oauth/jwks` and the
`.well-known` document **must** use the RFC 6749 §5.2 / RFC 7009 / RFC 8414
wire formats (`{"error":"invalid_grant"}`, `application/x-www-form-urlencoded`
input, `Cache-Control: no-store`), not `ApiResponse`. Proposal: put them in a separate
`OAuthProtocolController` with its own `@RestControllerAdvice` limited to that
controller, document it in `docs/coding-standards.md`, and keep `ApiResponse` for every `/api/oauth/**` endpoint.

### 4.2 Client registration — pre-registered, owner-approved

v1: **no open dynamic registration.** The admin registers each client
(`client_id` generated, `client_name`, exact `redirect_uris`, `allowed_scopes`,
`token_endpoint_auth_method=none` — public clients + PKCE). Disabling a client
revokes its grants. This matches today's `MCP_ALLOWED_CLIENT_IDS` model and the
issue's "approved client registration".

- Loopback redirects (`http://127.0.0.1:{port}/…`, `http://localhost:{port}/…`) for
  CLI clients: match scheme, host and path exactly, any port (RFC 8252 §7.3). No
  other wildcards.
- **[VERIFY] in staging:** whether each target client accepts a
  pre-registered `client_id`. If one requires DCR (RFC 7591) or a Client ID
  Metadata Document (MCP 2025-11-25), add it in phase 2 — gated by a
  redirect-URI allowlist (DCR) or a `client_id` URL allowlist (CIMD, fetched with
  an SSRF-safe client). Never open, unauthenticated registration.

### 4.3 Consent

- The Next.js page `/oauth/authorize` replaces `/oauth/consent`. It shows the client name,
  redirect host, the scopes in plain words, and "Connected apps" to revoke later.
- **[DECIDED] MFA:** **require `mfa_enabled`** for any MCP grant (as H-Vault does)
  and an `MfaStepUpService.verify(userId, code, "mcp-consent")` code on approve.
  Reason: approve hands a third party a 30-day credential. A stolen browser session
  alone must not be enough. Without MFA → 403 `OAUTH_MFA_REQUIRED`, which the page explains.
- Unverified-email / disabled users are already rejected by `JwtAuthenticationFilter`.
- An existing grant for the same (user, client, resource) is **replaced** on re-consent: one
  grant per pair, and the old grant's refresh tokens are revoked.
- Page headers: `frame-ancestors 'none'` (clickjacking), `Referrer-Policy: no-referrer`
  (the query carries `state`/`code_challenge`).

### 4.4 Token endpoint rules

`authorization_code`:
- code: 32 random bytes, SHA-256 stored, **60 s** TTL, single use via
  `UPDATE … SET used_at=now() WHERE id=? AND used_at IS NULL` (1 row or fail).
- **Replay of a used code → revoke the grant it produced** (OAuth 2.1 §4.1.3).
- `client_id`, `redirect_uri`, `resource` must equal what the code was bound to.
  `SHA256(code_verifier)` must equal the `code_challenge` (constant-time compare). Verifier length is 43–128.
- Any mismatch → `invalid_grant`, with the same message and timing for each cause.

`refresh_token`:
- Opaque, hashed, bound to a grant. Rotated on each use with the same conditional update.
- **Reuse of a rotated token → revoke the whole grant** (theft detection) + audit.
- TTL: 30 days sliding, **90 days absolute** per grant. Scope may only narrow.
- Re-checks on each refresh: user still enabled, still MFA-enabled (if required), client
  not disabled, grant not revoked.

## 5. Tokens

### 5.1 Access token — JWT, RFC 9068 profile

```
header  { "alg": "ES256", "typ": "at+jwt", "kid": "<kid>" }
payload { "iss": ISSUER, "aud": "<MCP resource URL>", "sub": "<user uuid>",
          "client_id": "<client id>", "scope": "hspace.read hspace.capture",
          "sid": "<grant uuid>", "jti": "<uuid>", "iat": …, "exp": iat+600 }
```

`sid` (the grant id, an opaque UUID) lets Spring check "grant still active" when the
token comes back to it (the §8.4a exchange). Next.js doesn't need it.

- **TTL 10 minutes.** Verification is local (JWKS), so the worst-case delay
  between revoking and an access token stopping working is 10 min. **[DECIDED]**
  no separate introspection endpoint in v1. The §8.4a exchange already re-checks
  the grant on every tool call, so a revoked grant stops data access at once.
  Revisit after staging.
- `ISSUER` = the API's public origin (e.g. `https://$API_DOMAIN`), from
  `app.oauth.issuer`. Caddy already forwards non-`/api/hub/*` paths to security-api.

### 5.2 Scopes

Taken from the h-space architecture doc; they map 1:1 to the current tools:

| Scope | Tools |
|---|---|
| `hspace.read` | `get_today_overview`, `list_goals`, `get_goal`, `list_tasks`, `get_goal_tasks`, `get_learning_progress`, `get_weekly_progress` (and `get_hengo_server_info`) |
| `hspace.capture` | `capture_reflection`, `capture_korean_phrase` |
| `hspace.plan` | `create_task`, `update_task`, `complete_task`, `create_goal`, `update_goal` |

The "write" in the issue's read/write split is `capture` + `plan`. No scope ever covers
H-Vault, finance or notes. Tokens only work at the MCP resource (`aud`), and Spring
APIs reject them (§5.4).

### 5.3 Keys

- ES256 (P-256) private key from env `OAUTH_SIGNING_KEY` (PKCS#8 PEM) + `OAUTH_SIGNING_KEY_ID`.
  Optional `OAUTH_PREVIOUS_PUBLIC_KEY` is published in JWKS during a rotation.
- `prod`: required, **no fallback**, fail fast at startup. `local`/`dev`/`test`: a key
  generated at startup (test tokens die with the process).
- Rotation runbook in `docs/deploy-vps.md`: add the new key, publish both, wait more than 10 min, drop the old one.

### 5.4 Token confusion guards (must have tests)

- An MCP ES256 token → any security-api / hub-api endpoint → 401 (HS512 parse fails).
- A browser HS512 token → `/mcp` → 401 (alg pinned to ES256, wrong `iss`/`aud`).
- An MFA-challenge or bridge token → `/mcp` → 401.
- A `typ` other than `at+jwt` → 401.

## 6. Metadata

`security-api` — `/.well-known/oauth-authorization-server`:

```json
{ "issuer": "https://API",
  "authorization_endpoint": "https://WEB/oauth/authorize",
  "token_endpoint": "https://API/oauth/token",
  "revocation_endpoint": "https://API/oauth/revoke",
  "jwks_uri": "https://API/oauth/jwks",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["none"],
  "revocation_endpoint_auth_methods_supported": ["none"],
  "scopes_supported": ["hspace.read", "hspace.capture", "hspace.plan"],
  "authorization_response_iss_parameter_supported": true,
  "client_id_metadata_document_supported": false }
```

The authorization endpoint lives on the web origin and the token endpoint on the
API origin, which RFC 8414 allows. Next.js `/.well-known/oauth-protected-resource[/mcp]`:
`authorization_servers: [ISSUER]`, `scopes_supported` as above. Remove the Next.js
mirror of `oauth-authorization-server`, or make it redirect to the issuer's document.

## 7. Data model — `security-api` migration `V7__oauth_authorization.sql`

```
oauth_clients        id uuid pk, client_id text unique, client_name, redirect_uris text[],
                     allowed_scopes text[], status (ACTIVE|DISABLED), created_by uuid fk users,
                     created_at, updated_at, version
oauth_grants         id uuid pk, user_id fk users ON DELETE CASCADE, client_pk fk oauth_clients,
                     resource text, scope text, created_at, absolute_expires_at,
                     last_used_at, revoked_at, revoked_reason,
                     UNIQUE (user_id, client_pk, resource) WHERE revoked_at IS NULL
oauth_authorization_codes  id, code_hash unique, grant_id fk, redirect_uri, code_challenge,
                     resource, scope, expires_at, used_at
oauth_refresh_tokens id, token_hash unique, grant_id fk ON DELETE CASCADE, expires_at,
                     rotated_at, revoked
```

- Every user-facing repository method is scoped by `userId`.
- Add expired codes and refresh tokens to `ScheduledTasks.cleanupExpiredTokens`: bulk
  delete, the existing job lock.
- New `AuditEventType` values: `OAUTH_CLIENT_REGISTERED`, `OAUTH_CLIENT_DISABLED`,
  `OAUTH_CONSENT_GRANTED`, `OAUTH_CONSENT_DENIED`, `OAUTH_TOKEN_ISSUED`,
  `OAUTH_REFRESH_REUSE_DETECTED`, `OAUTH_GRANT_REVOKED`. Details are ids, `client_id`
  and scope only — never codes, verifiers or tokens.
- `RateLimitingFilter`: add `/oauth/token`, `/oauth/revoke`, `/api/oauth/authorize/*`.

## 8. Next.js changes (h-space repo) — the frontend test contract

1. **Verifier** (`lib/mcp/auth.ts`): rename to `HspaceMcpTokenVerifier`. Algorithms
   `["ES256"]`, `typ: "at+jwt"`, `issuer` = `HSPACE_OAUTH_ISSUER`, `audience` = resource URL,
   JWKS = `${issuer}/oauth/jwks`, require `sub`, `client_id`, `scope`, `exp`, 30 s clock
   tolerance at most. `MCP_ALLOWED_CLIENT_IDS` becomes defense in depth (keep it, fail closed if empty).
2. **Scope enforcement**: register only the tools the token's scopes allow, and check the
   scope again inside each write handler. A missing scope → 403 `insufficient_scope` with
   `WWW-Authenticate: Bearer error="insufficient_scope", scope="hspace.plan"`.
   `MCP_WRITE_ENABLED` stays as a global kill switch.
3. **Authorize page** `/oauth/authorize`, replacing `/oauth/consent` (§4.3), plus a **Connected apps** settings page.
4. **Data path [DECIDED: (a)]**: the tools read goals and tasks from
   Supabase under RLS by forwarding the MCP token. A Spring-issued token can't pass RLS.
   Options:
   - **(a) Recommended interim — server-side exchange.** After verifying the MCP token,
     Next.js server calls `POST API/api/oauth/supabase-exchange` with the MCP token plus a
     server-only credential (`MCP_EXCHANGE_SECRET`, never `NEXT_PUBLIC_`). Spring re-verifies
     the token (sig, `aud`, grant not revoked — this also gives write calls immediate
     revocation) and mints a ≤5-min Supabase JWT for `sub`, used only inside that request.
     The MCP client never sees it, so it is not an MCP credential. Recorded as a **time-boxed exception** to
     "don't build on the bridge", deleted with the bridge.
   - (b) Next.js holds the Supabase JWT secret and mints tokens itself — rejected
     (spreads a secret that can impersonate any user).
   - (c) Tools call hub-api instead of Supabase — the right end state, but it is a data
     migration. The issue forbids doing it in the same change.
5. **Tests to add**: valid, expired, wrong `iss`, wrong `aud`, wrong `kid`/key, HS512
   browser token, `typ` mismatch, disallowed client, missing scope → 403 on each write
   tool, read-only token sees no write tools, two users see only their own rows (via
   the exchange + RLS), revoked grant → refresh fails and the exchange fails.

## 9. Spring test plan (real Postgres, synthetic data)

- **Authorize:** unknown client, mismatched or non-exact redirect, `plain` PKCE, missing
  challenge, bad scope, wrong `resource` → rejected without redirect. No MFA → 403.
  Wrong step-up code → counts toward the lockout.
- **Token:** happy path; code reused → grant revoked; expired code; wrong verifier; wrong
  client; wrong `redirect_uri`; wrong `resource`; concurrent double redeem → exactly one
  succeeds.
- **Refresh:** rotation; reuse → grant revoked; past absolute expiry; disabled client;
  user with MFA turned off (if required); scope widening refused.
- **Revoke:** the RFC 7009 endpoint and "Connected apps" DELETE. Another user's grant id → 404.
- **Isolation:** user B cannot list or revoke user A's grants. An admin can disable clients
  but has no endpoint that reads other users' grants (**[DECIDED]**: per client, admins see grant counts only, never user ids).
- **Confusion:** the §5.4 cases. **Browser session:** an MCP login does **not** revoke the
  browser refresh token.
- **Metadata/JWKS:** shape; the JWKS never contains private parts.

## 10. Rollout

1. PR 1 (this repo): V7 migration, keys, clients admin, authorize/approve/token/revoke,
   metadata, JWKS, grants page API, tests, docs (`security-api-state.md`, `database.md`,
   `architecture.md`, `.env.example` / `prod.env.example` names only).
2. PR 2 (this repo): the §8.4a exchange endpoint, behind config, off by default.
3. PR 3 (h-space): verifier, scopes, authorize page, connected apps, metadata, tests.
4. Staging: register the Claude (web + Code) and Codex clients. Do a full remote MCP
   login, tool calls with each scope, revoke and confirm the cutoff. Record the
   working **MCP protocol version(s)** (tests use `2025-06-18`; check `2025-11-25`)
   and SDK/client versions in `docs/chatgpt-mcp-integration.md`.
5. Production only after: keys set, rotation runbook, rate limits, audit queries, rollback =
   disable the clients (all grants dead within 10 min) + set the `MCP_WRITE_ENABLED=false` kill switch.

## 11. Non-goals

- No MCP access to H-Vault, finance or notes.
- No migration of notes or goals off Supabase in this change.
- No reuse of the bridge token as an MCP credential.
- No auth bypass for tests or local use.
- No copying of company-confidential data into H-Space.

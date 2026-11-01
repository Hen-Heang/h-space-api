# H-Vault on Spring + Neon — design

Status: **live in production since 2026-11-01, before the independent
security review.** Phases 1, 2 and the deploy part of 4 are done; the owner
turned the flag on (phase 5) ahead of the review (phase 3) — see section 10.
The review in section 9 is still required and still open.

## 1. Where things stand (2026-11-01)

- **Backend**: `hub-api` runs on the VPS behind Caddy (`/api/hub/*`,
  `docs/deploy-vps.md` §15). Tables `vault_keysets` and `vault_items` exist in
  the production Neon database.
- **Frontend** (`h-space/apps/web`, route `/vault`): v2 crypto and the
  `springVault` backend, with `NEXT_PUBLIC_FEATURE_VAULT_LIVE=true` in Vercel
  Production and Preview. Design: `apps/web/docs/h-vault.md`.
- **First real data**: the owner's vault was set up and a CSV imported on
  2026-11-01 — one keyset and 79 items, every item stored as a `v2.`
  envelope.
- **Supabase**: never held vault data (`kori_vault_accounts` was never
  created), so nothing was migrated. The vault doesn't touch Supabase.

Before this went live there was no vault data anywhere: no import, no
freeze, no Supabase jobs to replace, and no bridge in the path.

## 2. Goals and non-goals

**Goals**

- The server (Spring, Neon, backups, logs, admins) never sees a plaintext
  password or the key that decrypts it. It stores and returns ciphertext.
- Each user can only ever see, change or delete their own vault rows.
- The existing `/vault` UI keeps working. The data layer and encryption
  format change, and the list now appears only after unlocking (D1).
- The master password can be changed without re-encrypting every item.

**Non-goals (for now)**

- Autofill, browser extension, sharing, breach alerts, TOTP or passkey
  storage, attachments, free-text notes.
- Claiming "zero-knowledge" publicly. That needs the whole design and the
  deployed client reviewed (section 9).

## 3. Threat model

| # | Threat | Protected? | How / residual risk |
|---|---|---|---|
| T1 | Neon database or a backup leaks | **Yes** | Every item is stored as one ciphertext (D1): service names, emails, URLs and passwords alike. What leaks is only how many items a user has, their sizes and timestamps. Offline guessing of the master password is slowed by the KDF (section 5). |
| T2 | Someone with server access (VPS root, a bad deploy, a compromised `security-api`) | **Yes** for stored passwords | The server never holds the key. It *can* delete or withhold data, or serve modified frontend code (see T5). |
| T3 | Another signed-in user tries to read your items | **Yes** | Every query is scoped by the JWT's user id; other users' ids return 404 (tested). |
| T4 | Server swaps or replays ciphertext between items (e.g. shows the GitHub password under the bank entry) | **Yes in v2**, **no in v1** | v2 binds each ciphertext to its user and item id with AES-GCM associated data (section 5). The v1 envelope has no binding. |
| T5 | XSS or a malicious npm package runs in the `/vault` page | **No** while unlocked | Browser-side decryption means a compromised page can read passwords or capture the master password. Mitigations: CSP (none today, required before launch), few dependencies, 10-minute auto-lock. This is the main residual risk; it is accepted for every web-based vault. |
| T6 | Stolen H-Space login (password or session) | **Mostly** | Vault endpoints need an MFA-enabled account (D3), and even then the attacker only gets ciphertext: they still need the master password or recovery key. |
| T7 | Weak or reused master password | **Partly** | 12-character minimum, a "don't reuse your login password" warning (D5), KDF cost. A weak passphrase can still be brute-forced after T1. |
| T8 | Forgotten master password | **Yes, with the recovery key** (D2) | The printed recovery key unwraps the vault key. Lose both and the data is gone: nobody can decrypt, including the maintainer. |
| T10 | Stolen recovery key | **No** | It's as powerful as the master password. Shown once at setup, to print or store offline; never stored by H-Space in a usable form. Can be rotated (new key, old wrap replaced). |
| T9 | Clipboard snooping | **Partly** | 30-second auto-clear (already implemented). |

## 4. Changes to the frontend encryption (v1 → v2)

The current scheme (`lib/vault-crypto.ts`, envelope
`v1.<salt>.<iv>.<ciphertext>`) derives the encryption key straight from the
master password. It works, but has four gaps worth closing **before** any
real data exists, because changing them later means re-encrypting everything:

1. **No binding** (T4): a ciphertext decrypts fine under any row.
2. **No key check record**: unlock is proved by decrypting some existing item,
   so the first-run and "reset" flows depend on what happens to be stored.
3. **KDF settings are hard-coded**: raising the iteration count means a new
   envelope version and re-encrypting everything.
4. **Changing the master password** means re-encrypting every item.

v2 fixes all four with a standard two-level key hierarchy. There is no real
v1 data anywhere (only demo data in browsers), so v2 can replace v1 with no
migration.

## 5. Key hierarchy (v2)

```
master password ──PBKDF2-SHA256(salt, iterations)──▶ KEK (wrapping key, never stored)
                                                       │ AES-256-GCM wrap
random 256-bit vault key ◀─────────────────────────────┘  stored wrapped in vault_keysets
        │ AES-256-GCM, fresh 96-bit IV, AAD = user + item id
        ▼
item ciphertext (vault_items)
```

- **Vault key**: 32 random bytes from `crypto.getRandomValues`, made once per
  user. Imported into Web Crypto as non-extractable after unwrapping, and held
  only in tab memory (`lib/vault-session.ts`, same rules as today).
- **KEK** (key-encryption key): PBKDF2-SHA256 over the master password with a
  16-byte random salt and the stored iteration count (600,000 today). The
  salt and count are stored, so the cost can be raised later by re-wrapping
  one key.
- **Wrapped vault key**: `AES-GCM(KEK, iv, vaultKey, aad="h-vault/keyset/v2/<userId>")`.
  A wrong master password fails the GCM tag check, so unlock is "unwrap the
  key", not "try to decrypt some item".
- **Item ciphertext**: the whole item (service name, label, login identifier,
  URL, category, tags, favorite, status, login method, password) is serialized
  as JSON and encrypted as one `v2.<iv b64>.<ciphertext+tag b64>` envelope
  under the vault key, with `aad="h-vault/item/v2/<userId>/<itemId>"` (D1).
  The item id is a UUID generated in the browser before encrypting, and the
  server accepts it on create. The JSON is padded to a multiple of 256 bytes
  so lengths reveal less.
- **Recovery key** (D2): 32 random bytes, shown once at setup as base32 in
  groups (about 52 characters) to print or save offline. It's high-entropy, so
  it needs no slow KDF: `HKDF-SHA256(recoveryKey, info="h-vault/recovery/v2")`
  gives a second wrapping key, and the vault key is wrapped under it too
  (`recovery_wrapped_key`, AAD `"h-vault/recovery/v2/<userId>"`). Recovering =
  unwrap with the recovery key, then set a new master password. Rotating the
  recovery key replaces that one wrap.
- **Changing the master password**: unwrap with the old KEK, wrap with a new
  salt and KEK, and save one row (with optimistic locking). Items are untouched.
- **Reset** (both master password and recovery key lost): one server call
  deletes the keyset and every item in a single transaction, then a new
  keyset can be created. With item-level encryption nothing useful survives a
  reset, so the UI must say so plainly.
- **Argon2id** is the stronger KDF but isn't in Web Crypto and would need a
  WASM dependency in the page (more T5 surface). Stay on PBKDF2 for v2, and
  record the algorithm in `kdf` so a later switch only re-wraps the key.

## 6. Database (Neon, Flyway)

Draft only; final names follow the module chosen in D4.

```sql
-- One per user. Holds only wrapped copies of the vault key and the public KDF settings.
create table vault_keysets (
  user_id              uuid primary key references users(id) on delete cascade,
  kdf                  varchar(32)  not null check (kdf in ('PBKDF2-SHA256')),
  kdf_iterations       integer      not null check (kdf_iterations >= 600000),
  kdf_salt             varchar(64)  not null,  -- base64, 16 bytes
  wrapped_key          varchar(512) not null,  -- vault key under the master-password KEK
  recovery_wrapped_key varchar(512) not null,  -- vault key under the recovery-key KEK (D2)
  created_at           timestamptz  not null,
  updated_at           timestamptz  not null,
  version              bigint       not null default 0  -- optimistic locking
);

-- One per saved account. Everything about the account is inside the ciphertext (D1).
create table vault_items (
  id                 uuid primary key,  -- generated by the browser (part of the AAD)
  user_id            uuid not null references users(id) on delete cascade,
  payload_ciphertext varchar(16384) not null
                     check (payload_ciphertext ~ '^v2\.[A-Za-z0-9+/]+={0,2}\.[A-Za-z0-9+/]+={0,2}$'),
  created_at         timestamptz not null,
  updated_at         timestamptz not null,
  version            bigint      not null default 0
);
create index vault_items_user_updated_idx on vault_items (user_id, updated_at desc);
```

- There are no plaintext columns at all, so nothing can be stored unencrypted
  by mistake; the check constraint rejects anything that isn't a v2 envelope.
- What goes *inside* the payload is still limited by the frontend's rules: no
  PINs, tokens, recovery codes, API keys, TOTP seeds, private keys or
  free-text notes. The server can't enforce this; the frontend and the
  review do.
- `vault_keysets` row present = vault set up. `wrapped_key` and
  `recovery_wrapped_key` use the same envelope format as items.
- `on delete cascade` purges the vault when a user row is hard-deleted. Users
  are normally soft-deleted, so the account-deletion flow must also delete
  vault rows explicitly (tested).

## 7. API contract

Base path `/api/hub/vault` (D4), `ApiResponse` envelope, camelCase, JWT
required, email verified (already enforced by `JwtAuthenticationFilter`).

| Method + path | Body | Returns |
|---|---|---|
| `GET /keyset` | — | `{kdf, kdfIterations, kdfSalt, wrappedKey, recoveryWrappedKey, version}` or 404 if not set up |
| `PUT /keyset` | same fields + `version` (absent on first create) | keyset; 409 on version mismatch |
| `POST /reset` | `{confirm: "RESET"}` | 204; deletes keyset + all ciphertext in one transaction |
| `GET /items` | — | `VaultItem[]`, newest `updatedAt` first |
| `POST /items` | `{id, payloadCiphertext}` | `VaultItem` (201); 409 if the id exists |
| `PUT /items/{id}` | `{payloadCiphertext, version}` | `VaultItem`; 409 on version mismatch |
| `DELETE /items/{id}` | — | 204 |

`VaultItem` is `{id, payloadCiphertext, createdAt, updatedAt, version}`. The
browser decrypts it into today's `VaultAccount` (`apps/web/lib/vault.ts`), so
the UI components don't change; the list just appears after unlocking. Every
edit (including toggling a favorite) re-encrypts the whole item, so it's a
`PUT`, not a partial `PATCH`.

All endpoints answer **403** with `status.code` **40301**
(`VAULT_MFA_REQUIRED`) if the account doesn't have MFA enabled (D3). Other
errors: 400 validation (any non-v2 or oversized ciphertext, an unknown field,
malformed JSON), 401 no or invalid token, **404 for another user's item**
(never 403, so ids don't leak), 409 conflicts (stale `version`, duplicate id,
no keyset yet, vault full), 429 rate limited. `status.message` is safe to show
as-is.

## 8. Server-side rules

- The user comes **only** from the JWT principal. `user_id` is never read from
  a body or path. Every repository method takes `userId` (`findByIdAndUserId`,
  …); there is no unscoped finder, and admins have no endpoint to read
  another user's vault.
- **MFA required** (D3): every vault endpoint checks `users.mfa_enabled` for
  the caller and refuses with 403 otherwise. Turning MFA off doesn't delete
  the vault; it just becomes unreachable until MFA is turned on again.
- **Allowlisted fields only** (a request DTO, never the entity). Unknown
  fields are rejected with 400.
- **Validation**: ciphertext must match the v2 pattern and size limits; the
  server can't check what's inside, and doesn't try.
- **Logging**: never log request or response bodies, ciphertext or wrapped
  keys. Log ids and outcomes only.
- **Audit** (`AuditLogService`, new `AuditEventType`s): `VAULT_KEYSET_CREATED`,
  `VAULT_KEYSET_CHANGED`, `VAULT_RESET`, `VAULT_ITEM_CREATED`,
  `VAULT_ITEM_UPDATED`, `VAULT_ITEM_DELETED`, with the item id and no content.
- **Rate limiting**: writes to `/reset` and `/keyset` share
  `RateLimitingFilter`'s limit (5 per minute per client); reading the keyset
  (every unlock) isn't limited. Item writes aren't limited either, because a
  CSV import creates up to 500 items one request at a time; the item cap
  bounds them instead. Master-password guessing happens offline in the
  browser, so the server can't rate-limit it; that's why KDF cost matters (T7).
- **Limits**: at most 1,000 items per user; an item's ciphertext at most
  16,384 characters.
- **Deleted accounts**: users are soft-deleted, so `VaultCleanupTask` purges
  the vault rows of deleted accounts every hour.

## 9. Independent security review (gate before real data)

Give the reviewer this document, `lib/vault-crypto.ts` (v2),
`lib/vault-session.ts`, the vault components, and the Spring vault package.
Ask them to check:

- Key hierarchy, AAD binding, IV uniqueness, KDF parameters, non-extractable
  keys, wrong-password handling.
- Nothing decrypted or derived ever reaches the network, storage, logs, the
  React Query cache or error reporting.
- The page's CSP and third-party scripts on `/vault`.
- Server authorization (other-user ids, admin paths), validation and logging.
- Reset and master-password-change flows can't lose or mix keys.

Findings are fixed and re-checked before the live flag goes on.

## 10. Rollout

| Phase | What | Real passwords? |
|---|---|---|
| 0 | This document reviewed; D1–D5 answered | No |
| 1 | Backend: module, Flyway migration, endpoints, tests (fictional data only) | No |
| 2 | Frontend: v2 crypto, keyset flows, `springVaultAdapter` behind a flag, CSP | No |
| 3 | Independent review (section 9); fix findings | No |
| 4 | Deploy to the VPS; end-to-end test with fictional entries; restore drill from a Neon backup | No |
| 5 | Turn the flag on; enter real passwords | **Yes** |

**What actually happened (2026-11-01).** Phases 0–2 were done in order, then
hub-api was deployed (the deploy half of phase 4). The owner then chose to
turn the flag on in Production and import real accounts **before** phase 3.
Still outstanding, in this order:

1. Phase 3 — the independent review (section 9). Fix findings; if a finding
   affects the stored format, plan a re-encryption, not just a code change.
2. Phase 4 — the restore drill: restore a Neon backup to a branch and unlock
   the vault from it.

Until both are done, treat the vault as unreviewed: don't advertise it as
"zero-knowledge", and keep the rollback below ready.

**Rollback**: turn the flag off and the UI returns to demo mode. Data in Neon
stays encrypted and untouched; nothing to reconcile, because nothing else
writes vault data.

## 11. Tests

- **Backend** (real Postgres test schema): account without MFA → 403 on every
  endpoint; CRUD; another user's item →
  404 on GET/PUT/DELETE and absent from lists; no or invalid token → 401;
  non-v2 or oversized ciphertext → 400; unknown field → 400; version
  conflicts → 409; reset deletes keyset and ciphertext atomically; account
  deletion purges the vault; nothing sensitive in logs (captured-log
  assertion); the migration applies and `validate` passes.
- **Frontend**: v2 round trip; AAD mismatch (swapped item id or user id) fails
  to decrypt; wrong master password fails unwrap; recovery key unlocks and sets
  a new master password; master-password change and recovery-key rotation
  keep every item readable; reset; requests contain no plaintext item fields,
  only v2 envelopes (request-capture test).

## 12. Decisions (answered 2026-11-01)

| # | Decision | Chosen |
|---|---|---|
| D1 | Metadata | **Encrypt the whole item.** A leak shows nothing about which accounts exist. The list appears only after unlocking. |
| D2 | Forgotten master password | **Printed recovery key** that also wraps the vault key (section 5). |
| D3 | MFA | **Required**: vault endpoints refuse accounts without MFA enabled. |
| D4 | Where the code lives | **New `hub-api` module** (`/api/hub/**`), vault in its own package, as hub-api's first area (it needs no data migration, unlike notes). One extra container on the VPS, about 512 MB limit; Caddy routes `/api/hub/*` to it. |
| D5 | Reusing the login password as the master password | **Warn only**, with clear setup copy. A strict check would mean re-asking for the login password, and Google/email-code users have none. Revisit if the review asks for it. |

## 13. Progress

- **Phase 1 (backend), done 2026-11-01**: `hub-api` module with Flyway
  `V1__vault.sql`, `VaultController` → `VaultService` → repositories, MFA
  check, audit events, cleanup task, and the backend tests in section 11
  (`VaultControllerIntegrationTest`).
- **Phase 2 (frontend), done 2026-11-01** (h-space PR #41): v2 crypto with
  the key hierarchy and recovery key, setup/unlock/recovery/reset screens,
  the `springVault` backend behind `NEXT_PUBLIC_FEATURE_VAULT_LIVE`, and a
  Content-Security-Policy (`lib/csp.ts`).
- **Deploy, done 2026-11-01** (PR #18): hub-api image built by CI, run with
  the `hub` compose profile, `/api/hub/*` routed by Caddy. Migrations created
  `vault_keysets` and `vault_items` in Neon.
- **Flag on in Production, 2026-11-01** — before the review (section 10).
  The owner's first import is stored: 1 keyset, 79 `v2.` items.
- **Open**: independent review (section 9), restore drill (phase 4).

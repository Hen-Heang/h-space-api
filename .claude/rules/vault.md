# Vault Rule (H-Vault, `hub-api`)

Full detail: `docs/vault-design.md` (read it before touching anything under
`hub-api/**/vault/**` or `db/hub-migration/`), root `CLAUDE.md` "Vault and
authentication safety gate".

- H-Vault holds **real** credentials in production (since 2026-11-01) and the
  independent security review is **still pending**. Until it's done:
  - don't widen the vault — no new secret fields, sharing, export, or admin
    access to vault rows;
  - don't describe it as "zero-knowledge" anywhere (code, docs, UI copy);
  - treat review findings as fixes to ship, possibly with re-encryption.
- The server stores and returns **ciphertext only** (`v2.<iv>.<ct+tag>`). It
  never receives a master password, recovery key or plaintext item, and must
  not start to. Don't add server-side decryption or key derivation.
- The user id comes **only** from the JWT principal — never from a body or
  path. Every repository method is scoped by `userId`; no unscoped finders.
- Every vault endpoint requires `users.mfa_enabled` (403
  `VAULT_MFA_REQUIRED` otherwise). Don't add a bypass, including for tests
  — enable MFA on the test user instead.
- Request DTOs are allowlists that reject unknown fields; never bind the
  entity directly.
- **Never log** request/response bodies, ciphertext, wrapped keys or salts.
  Log ids and outcomes only. Audit events carry the item id, no content.
- Keep the limits (1,000 items/user, 16,384-char ciphertext) and the
  `/reset` + `/keyset` rate limit.
- Vault schema changes go in `hub-api/src/main/resources/db/hub-migration/`
  (own history table `flyway_hub_schema_history`), never in
  `security-api`'s `db/migration/`.
- Tests use synthetic ciphertext only. Never put a real password, real
  vault export or real user data in tests, fixtures, prompts or logs.

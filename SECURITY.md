# Security Policy

## Reporting a vulnerability

This is currently a personal/practice project without a dedicated security
contact. If you find a vulnerability, please open a GitHub issue on the
[repository](https://github.com/Hen-Heang/authhub) with as much detail as
possible (affected module, reproduction steps, impact). Avoid filing public
issues for anything actively exploitable against a real deployment — there is
none in production today, so this is a soft requirement for now.

## Secrets handling

- **Never commit real secrets.** All `application*.yml` files read secrets
  (`JWT_SECRET`, `DB_PASSWORD`, `MAIL_PASSWORD`, ...) from environment
  variables — see `.env.example` and `docs/database.md`.
- `application-local.yml` (per module) is git-ignored and only carries
  non-production fallback defaults for zero-config local dev.
- `application-test.yml` has one fixed, non-sensitive JWT secret default used
  purely for automated tests/CI — it is not a production credential.
- `application-prod.yml` has **no fallback defaults**: startup fails fast if
  required env vars are missing, rather than running with a weak or absent
  secret.

### Known historical exposure

Prior to this cleanup, `security-api/src/main/resources/application-local.yml`
was committed to git with a real-looking hard-coded JWT secret, and
`todoapi/src/main/resources/application.yml` had a hard-coded JWT secret with
no environment override. Both have been replaced with environment-variable-based
config, and `application-local.yml` has been removed from git tracking going
forward.

**The old secret values are still present in git history** (this repo has not
had its history rewritten). If this repository is ever made public or shared
beyond trusted local development, treat those historical secrets as
compromised: rotate `JWT_SECRET` in every environment, and consider scrubbing
history (e.g. `git filter-repo`) before publishing.

## Supported versions

Only the `main` branch is supported. There are no released versions yet (see
[ROADMAP.md](ROADMAP.md)).

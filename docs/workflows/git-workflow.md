# Git Workflow

## Commits

- Never commit unless explicitly asked (see root `CLAUDE.md`).
- Keep commits logically scoped — one concern per commit — with a clear
  "why" in the message, not just "what" changed.
- Never commit real secrets. `application-local.yml` and `.env` are
  git-ignored — use them for local overrides (see `docs/database.md`,
  `SECURITY.md`).

## `main` is mid-refactor

`security-api` is actively having OTP and the old cookie-based OAuth2 flow
removed while MFA, audit logging, and rate-limiting are added (full detail:
`docs/security-api-state.md`). Before staging files:

1. Run `git status` and `git log` first.
2. Don't sweep unrelated in-progress changes into a new commit — stage only
   what belongs to the change you're making.
3. If you see stale references to removed classes (custom OTP
   controller/service, `HttpCookieOAuth2AuthorizationRequestRepository`,
   `OAuth2*Handler`, `OAuth2UserInfo*`, `CookieUtils`), treat them as stale
   and flag it rather than resurrecting the pattern.

## Branches / PRs

- Branch off `main`, open a PR back into `main`.
- CI (`.github/workflows/ci.yml`) runs `./gradlew clean build` against a real
  Postgres 16 service container on every push/PR to `main` — must be green
  before merge.
- See `code-review-workflow.md` for review expectations and
  `release-workflow.md` for what happens after merge.

## Destructive operations

Force-push, `git reset --hard`, amending pushed commits, or branch deletion
require explicit user confirmation — never run these as a shortcut past a
failing hook or conflict.

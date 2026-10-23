# Git Rule

Full detail: root `CLAUDE.md`, `docs/workflows/git-workflow.md`.

- Never commit unless explicitly asked.
- `main` currently has an in-progress refactor (OTP/OAuth2 removal,
  MFA/audit/rate-limiting addition — see `docs/security-api-state.md` and
  `.claude/rules/security.md`). Before staging files, run `git status` and
  `git log`, and don't sweep unrelated in-progress changes into a new commit.
- Keep commits logically scoped — one concern per commit — with a "why" in
  the message.
- Never commit real secrets (see `.claude/rules/security.md`).
- Destructive operations (force-push, `reset --hard`, amending pushed
  commits, branch deletion) require explicit user confirmation — never use
  them as a shortcut past a failing hook or merge conflict.
- Create new commits rather than amending, unless explicitly asked to amend.

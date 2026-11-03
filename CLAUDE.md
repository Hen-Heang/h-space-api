# H-Space API — Claude Code entrypoint

The shared rules for every agent live in `AGENTS.md` (Codex reads it
directly). Edit rules there, not here, so Claude and Codex never disagree.

@AGENTS.md

## Reference docs (loaded for Claude)

@docs/architecture.md
@docs/security-api-state.md
@docs/coding-standards.md
@docs/database.md
@docs/build-and-run.md

## Claude-specific

- `.claude/rules/` holds topic rules (incl. `vault.md`), `.claude/commands/`
  the task checklists, `.claude/context/` background notes — see
  `.claude/CLAUDE.md`.
- `.claude/settings.json` denies secret reads and destructive git; the
  `.claude/hooks/` guards block commits/pushes on `main` and writes that
  look like real secrets. Don't work around a hook — fix the cause or ask.

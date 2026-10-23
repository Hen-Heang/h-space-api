# Documentation Update Prompt

Use when code changes need matching doc updates.

```
Update docs for: {{change summary}}.

Check each of these for whether it needs an update — don't touch ones that
aren't affected:
- docs/architecture.md — module boundaries/dependency direction changed?
- docs/security-api-state.md — auth model in/out list changed (new
  controller/service added or removed, MFA/audit/rate-limiting/Google-login
  behavior changed)?
- docs/coding-standards.md — a new convention introduced (new mapper
  pattern, new envelope type, new package)?
- docs/database.md — schema/connection/secret-handling assumptions changed?
- docs/build-and-run.md — a new Gradle task, port, or run command added?
- docs/workflows/*.md — a workflow step changed (new CI step, new pre-PR
  command, new release step)?
- CHANGELOG.md [Unreleased] — always, for any user/developer-visible change
  (Added/Changed/Fixed/Security).
- README.md — only if it documents the specific thing changed (profiles,
  prerequisites, endpoints list).

Constraints:
- Keep each doc's existing scope — don't merge unrelated topics into one
  file, don't invent new top-level docs files without asking.
- If docs/security-api-state.md is out of date relative to actual code
  (stale OTP/OAuth2 references, missing new controller), flag it explicitly
  rather than silently trusting old text.
```

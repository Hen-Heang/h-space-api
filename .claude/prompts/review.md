# Review Prompt

Use to ask for a review of a diff, PR, or specific files.

```
Review {{diff / PR #NNN / file(s)}} for {{correctness | security | both}}.

Checklist (.claude/rules/review.md):
- Module dependency direction not violated (.claude/rules/architecture.md).
- ApiResponse/ApiStatus/StatusCode envelope used for all responses — no raw
  DTOs or bare ResponseEntity<T> bodies.
- Exceptions are domain/custom, handled by GlobalExceptionHandler — not
  caught-and-formatted in controllers.
- No real secrets committed (.claude/rules/security.md).
- New behavior has a test (.claude/rules/testing.md).
- If security-api is touched: nothing removed per
  docs/security-api-state.md (OTP, old cookie-based OAuth2) has been
  reintroduced.
- ./gradlew clean build passes.

Output: list findings ranked by severity; note anything that's a style
nit vs. an actual correctness/security issue.
```

For a deeper pass, use the `/code-review` skill directly instead of asking
Claude to review free-form (`/code-review high|max` for larger diffs,
`/code-review ultra` for a multi-agent cloud review — user-triggered only).

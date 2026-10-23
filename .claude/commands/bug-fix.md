# Bug Fix Prompt

Use for fixing broken/incorrect behavior.

```
Fix: {{one-line description of the bug}}

Module: {{common-api|security-api|open-api}}

Repro:
- {{steps to reproduce, or failing test name}}

Expected vs actual:
- Expected: {{...}}
- Actual: {{...}}

Constraints:
- Find the root cause before patching — don't paper over a symptom with a
  try/catch or a default value.
- Fix belongs in the layer that owns the invariant being violated
  (controller/service/repository/security) — don't fix a service bug in the
  controller.
- Don't touch unrelated in-progress refactor work on main
  (docs/security-api-state.md, .claude/rules/git.md) — scope the diff to the
  bug only.
- Exceptions still go through GlobalExceptionHandler, responses still wrapped
  in ApiResponse/ApiStatus/StatusCode.

Deliverables:
- A regression test that fails before the fix and passes after.
- ./gradlew clean build passing.
- CHANGELOG.md [Unreleased] → Fixed entry.
```

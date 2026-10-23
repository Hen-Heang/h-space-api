# Refactor Prompt

Use for restructuring code without changing observable behavior.

```
Refactor {{target: class/package/module}} to {{goal, e.g. "extract the token
validation logic into its own service"}}.

Constraints:
- Behavior must stay identical — this is not the place to also fix bugs or
  add features. Flag anything you notice separately instead of bundling it.
- Don't cross module dependency direction while moving code
  (common-api → security-api → open-api, .claude/rules/architecture.md).
- Keep the resulting code in the standard package layout (config/
  controller/ domain/ repository/ security/ service/(+impl/) payload/
  exception/) — don't invent new top-level packages.
- Existing tests must keep passing unchanged in intent (rename/relocate is
  fine; behavior assertions shouldn't need to change). If a test breaks, that
  usually means behavior changed — stop and check.
- No half-finished intermediate state — either the refactor is complete in
  this diff or don't start it.

Deliverables:
- ./gradlew spotlessApply && ./gradlew clean build passing with no test
  changes beyond mechanical relocation.
- Note in the PR description what moved where, for reviewers.
```

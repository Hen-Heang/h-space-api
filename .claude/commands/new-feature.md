# New Feature Prompt

Use for adding new user-facing behavior (endpoint, service capability, etc.).

```
Add {{feature}} to {{module: common-api|security-api|open-api}}.

Context:
- {{why this is needed / linked issue}}

Requirements:
- {{behavior, inputs/outputs, edge cases}}

Constraints:
- Respect module dependency direction: common-api → security-api → open-api
  (.claude/rules/architecture.md). Don't introduce a cycle.
- Package layout: config/ controller/ domain/ repository/ security/
  service/(+impl/) payload/ exception/ — put new code in the matching
  package (.claude/rules/coding-style.md).
- Lombok for boilerplate, MapStruct for DTO<->entity mapping.
- Wrap the response in ApiResponse/ApiStatus/StatusCode — no raw DTOs or
  bare ResponseEntity<T> bodies.
- Throw domain/custom exceptions; let GlobalExceptionHandler translate them
  — don't catch-and-format in the controller.
- If touching security-api, check docs/security-api-state.md first — don't
  resurrect removed OTP/OAuth2-cookie code.

Deliverables:
- Implementation across the relevant layers.
- Tests covering the new behavior (.claude/rules/testing.md).
- ./gradlew spotlessApply && ./gradlew clean build passing.
- CHANGELOG.md [Unreleased] entry if user-visible.
```

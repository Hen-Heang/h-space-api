# Release Workflow

**Current state: no versioned releases yet.** All modules share version
`0.0.1-SNAPSHOT` (set once in the root `build.gradle`'s `subprojects {}`
block per `docs/architecture.md`), and `CHANGELOG.md` keeps everything under
`[Unreleased]`. Treat anything below as the process to follow *when* the
first release happens, not something already in effect.

## Changelog discipline (already in effect)

- Every notable change goes under `CHANGELOG.md`'s `[Unreleased]` section,
  loosely following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
  `Added` / `Changed` / `Fixed` / `Security` subsections.
- Write entries in terms of user/developer-visible effect, not the diff.

## Cutting a release (once versioning starts)

1. Confirm `main` is green: CI (`.github/workflows/ci.yml`) passing, no
   in-progress refactor work half-merged (check `docs/security-api-state.md`
   isn't describing a mid-flight state).
2. Bump the shared version in the root `build.gradle` (single place — do not
   set per-module versions, see `docs/architecture.md`).
3. Move `[Unreleased]` entries in `CHANGELOG.md` under a new `[x.y.z] -
   YYYY-MM-DD` heading.
4. Tag the commit (`vX.Y.Z`) and push the tag.
5. `./gradlew clean build` one more time against the tag before publishing
   any artifact.

## Secrets

Never bake real secrets into a release build. `jwt.secret` and DB
credentials must come from environment variables in anything beyond local
dev — see `docs/database.md` and `SECURITY.md`.

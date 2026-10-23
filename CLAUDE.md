# AuthHub

Multi-module Gradle/Spring Boot monorepo. Group `com.henheang`, Java 17, Spring Boot 3.5.15, PostgreSQL.

@docs/architecture.md
@docs/security-api-state.md
@docs/coding-standards.md
@docs/database.md
@docs/build-and-run.md

## Git Rules

- Never commit unless explicitly asked.
- This repo currently has an in-progress refactor on `main` (OTP/OAuth2 removal, MFA/audit/rate-limiting addition) — before staging files, check `git status` and don't sweep unrelated in-progress changes into a new commit.

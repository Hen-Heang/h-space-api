# Code Review Checklist

Things to look for when reviewing PRs in this repo, beyond what `/code-review` covers
automatically.

- [ ] Responses wrapped in `ApiResponse`/`ApiStatus`/`StatusCode`, not raw DTOs
- [ ] DTO <-> entity mapping via MapStruct, not hand-written
- [ ] Boilerplate via Lombok, not hand-written getters/builders/constructors
- [ ] New code in the right package (`config/controller/domain/repository/security/service/payload/exception`)
- [ ] No cross-module dependency cycles (`common-api` <- `security-api` <- `open-api`)
- [ ] Tests added/updated, `./gradlew build` passes

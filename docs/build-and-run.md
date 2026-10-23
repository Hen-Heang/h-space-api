# Build & Run

```bash
./gradlew build                    # build all modules, run tests
./gradlew :security-api:build      # build one module
./gradlew build -x test            # skip tests

./gradlew :security-api:bootRun    # port 8080
./gradlew :open-api:bootRun         # port 8082, needs security-api's JWT to authenticate

./gradlew test                     # all tests, JUnit 5
./gradlew :security-api:test       # one module
```

Run `./gradlew build` before considering any change done — don't just eyeball the diff.

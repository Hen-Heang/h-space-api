# syntax=docker/dockerfile:1
#
# Image for security-api (port 8080). Build from the repo root:
#   docker build -t h-space/security-api .
# Runtime config comes only from environment variables (see .env.example);
# no secrets are baked into the image.

# ---- build: compile the Spring Boot jar ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY . .
# Tests run in CI (./gradlew clean build) against a real Postgres; the image
# build only packages the jar.
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew :security-api:bootJar --no-daemon \
 && cp "$(find security-api/build/libs -name '*.jar' ! -name '*-plain.jar')" app.jar

# Split the jar into layers so a code-only change doesn't re-ship the
# (rarely changing) dependency layer.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# HEALTHCHECK below needs wget. The base image ships it today, but it's not
# part of its contract (curl was dropped), so install it if it's ever missing.
RUN command -v wget >/dev/null \
 || (apt-get update && apt-get install -y --no-install-recommends wget \
     && rm -rf /var/lib/apt/lists/*)

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app

# Size the heap from the container memory limit instead of host RAM, leaving
# ~40% for metaspace, code cache, thread stacks and native buffers (a 768 MB
# limit gives a ~460 MB heap). Exit on OOM so the restart policy brings up a
# fresh JVM instead of leaving a broken one running.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Liveness, not /actuator/health: it doesn't query the database, so a DB
# outage doesn't get the container restarted, and a scale-to-zero database
# (Neon) isn't kept awake by a check every 10 seconds.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

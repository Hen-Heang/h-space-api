# Deploying to Railway

Where h-space-api runs (owner decision, 2026-11-25). It replaced the VPS
setup in `docs/deploy-vps.md`, which is kept for reference only.

**What it is now:** a learning and portfolio sandbox. H-Space's web app
doesn't call it: since 2026-11-21 sign-in, the inbox and H-Vault run on
Vercel + Supabase + Neon (h-space `docs/SPRING_EXIT_PLAN.md`). So this
service must stay isolated from H-Space's data (see "Never" below).

## 1. The service

| Setting | Value (Railway dashboard, 2026-11-25) |
|---|---|
| Project / service | `tranquil-learning` / `h-space-api` (Hobby plan) |
| Source | GitHub `Hen-Heang/h-space-api`, branch `main`, deploys on every push |
| Builder | Dockerfile, path `/Dockerfile` |
| Region / replicas | asia-southeast1 / 1 |
| Restart policy | on failure, max 10 retries |
| Networking | not exposed (no public domain yet) |

The image is the same one CI builds and smoke-tests (`Dockerfile`,
`docs/build-and-run.md`). It packages one module, `security-api` by
default, and always listens on **8080** (`SERVER_PORT` is set in the
image). Railway's own `PORT` variable isn't used.

- **Another module:** the `MODULE` build argument picks it (e.g.
  `hub-api`). Railway passes service variables to Docker builds as build
  arguments, so a `MODULE` service variable should work — **verify on first
  use** in the build log (`:hub-api:bootJar`).
- **No BuildKit cache mounts.** Railway rejects `RUN --mount=type=cache`
  without a Railway-specific id (`s/<service-id>-…`), which is why the
  Dockerfile has none (#40). Keep it that way.
- **CI is still the gate.** Railway builds whatever lands on `main`; only
  merge PRs whose `build` and `image` jobs passed.

## 2. Database

Use a database of its own:

- **Recommended: a separate Neon project** with a small compute (max
  0.25–1 CU) and scale-to-zero. Use the **direct** endpoint (host without
  `-pooler`): Flyway migrates through the app's pool at startup, and a
  transaction pooler breaks it (`docs/database.md`).
- Railway Postgres also works, but it runs all the time and its memory
  counts against the plan.

The prod pools drain to zero when idle (`minimum-idle: 0`, 60 s idle
timeout, #39), so Neon can suspend between requests. **Keep it that
way**: one idle connection kept Neon awake 24/7 in November 2026 and used
up the whole free quota (`docs/database.md`). After the first deploy,
check that the compute suspends within ~5 quiet minutes (Neon console, or
`neonctl operations list --project-id <id>`).

## 3. Variables

Set under the service's **Variables** tab. Values never go in the repo or a
chat. Names match `deploy/prod.env.example`, minus the VPS-only ones
(`API_IMAGE`, `API_DOMAIN`, `ACME_EMAIL`, `API_MEM_LIMIT`, `COMPOSE_PROFILES`,
`HUB_*`).

| Variable | Needed | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `prod` — no fallback secrets; startup fails if one is missing |
| `DB_URL` | yes | `jdbc:postgresql://HOST:5432/DB`; TLS is enforced (`sslmode=require` unless stricter) |
| `DB_USERNAME`, `DB_PASSWORD` | yes | The sandbox database's role |
| `JWT_SECRET` | yes | New, ≥ 64 bytes Base64 (`openssl rand -base64 64 \| tr -d '\n'`). Never the VPS-era value |
| `MFA_ENCRYPTION_KEY` | yes | New, 32 bytes Base64. Don't change it once anyone enables MFA |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated origins; startup fails if empty or `*` |
| `FRONTEND_URL` | for emails | Where email links point (default `http://localhost:3000`) |
| `OWNER_EMAIL` | recommended | Becomes the admin once verified; otherwise sign-up is invite-only with no one to invite |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | for emails | SMTP app password; the defaults are placeholders, so email codes fail without them |
| `DB_POOL_MIN_IDLE` | no | Leave unset (0). Never 1 on Neon |
| `API_DOCS_ENABLED` | no | `true` turns on Swagger UI; turn it off again after use |

## 4. Never

- **Never point it at H-Space's databases** — Neon `hspace` (H-Vault) or
  `luyra` (Finance). The sandbox has no business holding real credentials
  or money records, and a runaway pool there takes H-Space down (it did in
  November).
- **Never set `SUPABASE_URL`, `SUPABASE_JWT_SECRET` or
  `SUPABASE_SERVICE_ROLE_KEY`.** They turn on the temporary token bridge,
  which can mint a session for any user of the Supabase project H-Space
  shares with Orbit — the main security reason Spring left production.
- **Never reuse the VPS's `JWT_SECRET` or `MFA_ENCRYPTION_KEY`.**

## 5. First deploy and checks

1. Create the database and set the variables above.
2. Deploy (push to `main`, or **Redeploy** in the dashboard). Expect
   Flyway's migrations, then `Started SecurityApiApplication` in the
   deploy logs.
3. **Health check:** under Settings → Deploy, set the healthcheck path to
   `/actuator/health/liveness` — it answers without touching the database,
   so a database blip doesn't fail a deploy. (Railway uses its own check,
   not the Dockerfile's `HEALTHCHECK`.)
4. **If you expose it:** Settings → Networking → generate a domain with
   target port **8080**, then `curl https://<domain>/actuator/health`
   → `{"status":"UP"}`.
5. **Client IPs (verify on first exposed deploy):**
   `forward-headers-strategy: native` trusts `X-Forwarded-For` only from
   private addresses (`docs/build-and-run.md`). Check that the audit log
   (`audit_logs.ip_address`) shows real client IPs, not Railway's proxy. If
   it shows the proxy, set `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to
   Railway's proxy range — never to `.*`, which lets any client choose its
   own rate-limit bucket.
6. Confirm the database suspends after ~5 quiet minutes (section 2).

## 6. Operating it

- **Logs:** the service's **Deployments → View logs** (build and deploy),
  and **Observability** for runtime logs. Every line carries the request id
  (`docs/architecture.md`).
- **Rollback:** **Deployments**, pick the last good one → **Redeploy**.
  Flyway migrations are forward-only, so a rollback across a migration
  needs the matching database state — take a `pg_dump` (or a Neon branch)
  before merging a migration.
- **Cost:** Hobby includes $5 of usage a month; watch **Usage**. A
  768 MB JVM running all the time can exceed it; Railway's app sleeping
  (Settings → Deploy → Serverless) stops the container when idle, at the
  cost of a JVM cold start on the next request.
- **Backups:** the database provider's (Neon point-in-time restore on its
  plan) plus your own `pg_dump` before risky changes. Rehearse a restore
  into a Neon branch now and then.

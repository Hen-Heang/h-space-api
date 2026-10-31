# Deploying to a VPS

A step-by-step runbook for running `security-api` on one small Linux server
with the files in `deploy/`, using a **managed PostgreSQL** (Neon or
Supabase). It assumes no prior server experience; commands marked
**(laptop)** run on your own computer, everything else on the server.

What runs where:

```
                         VPS (Docker, deploy/compose.prod.yaml)
internet ──80/443──> caddy (HTTPS) ──> security-api ──TLS──> Neon / Supabase
```

- Only Caddy is reachable from the internet. The API has no published port.
- No database runs on the VPS, so nothing on it needs backing up except
  `.env` (keep it in a password manager) — the data lives with the provider.
- Only `security-api` is deployed; `open-api` and `finance-api` are not.

---

## 0. What you need

- **A VPS**: Ubuntu 24.04 LTS, **1 vCPU / 2 GB RAM / 20 GB disk** is the
  minimum this setup is sized for (the API is capped at 768 MB, Caddy at
  256 MB). A public IPv4 address, root SSH access, and ports 80/443 open at
  the provider.
- **A managed PostgreSQL database** — see §1.
- **A domain** you control, e.g. `api.yourdomain.com`.
- **An SSH key** on your laptop. Check with `ls ~/.ssh/id_ed25519.pub`;
  create one with `ssh-keygen -t ed25519` if it's missing. Add the public
  key when creating the VPS.
- **A Gmail app password** (or other SMTP account) for verification and
  sign-in emails.

## 1. Create the database

Use a **new, empty database just for this API**. Don't point it at the
H-Space web app's existing Supabase database: Flyway refuses to migrate a
non-empty `public` schema, and the API's tables (password hashes, refresh
tokens, audit log) don't belong next to data served to browsers.

Pick a region close to Cambodia (e.g. Singapore) — every query crosses that
distance.

### Choosing the database endpoint

The API runs **Flyway migrations at startup through the same connection
pool it serves requests with** (`DB_URL`). Flyway holds a *session-level*
advisory lock while migrating, and the JDBC driver uses server-side prepared
statements, so `DB_URL` must be a **session** endpoint — not a
transaction-mode pooler. A pool of 5 long-lived connections from one server
doesn't need a pooler anyway.

| Provider | Use for `DB_URL` | Don't use |
|---|---|---|
| **Neon** | The **direct** endpoint: host *without* `-pooler`, port 5432. | The `-pooler` host (PgBouncer in transaction mode). |
| **Supabase** | The **session pooler**: `aws-0-<region>.pooler.supabase.com:5432`, user `postgres.<project-ref>` (works over IPv4). Or the direct `db.<project-ref>.supabase.co:5432` *if the VPS has IPv6* — Supabase's direct endpoint is IPv6-only without the paid IPv4 add-on. | The transaction pooler on port **6543**. |

If you later want app traffic through a pooler, keep `DB_URL` on it and add
`SPRING_FLYWAY_URL` / `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`
pointing at the direct endpoint (see `prod.env.example`); with a
transaction pooler you'd also need `prepareThreshold=0` in `DB_URL`.

### JDBC URL format

The dashboards show a `postgresql://user:password@host/db` URI. The API
needs the **JDBC** form, with the user and password as separate settings:

```
DB_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=require
DB_USERNAME=<user>
DB_PASSWORD=<password>
```

The `prod` profile requires TLS to the database even if you forget
`sslmode` (`spring.datasource.hikari.data-source-properties.sslmode`), and
refuses to start against a server that doesn't offer it. `sslmode` in
`DB_URL` overrides that default: only ever make it stricter. `require`
encrypts but doesn't verify the server's certificate; to verify it as well:

- **Neon** (publicly trusted certificate):
  `?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`
- **Supabase** (its own CA): download the CA certificate from the
  dashboard's database settings, mount it into the container, and use
  `?sslmode=verify-full&sslrootcert=/path/in/container/ca.crt`.

### Provider-specific notes

- **Neon** stops (scales to zero) a compute after 5 minutes without
  queries, closing its connections; the next request wakes it in a few
  hundred ms and the pool replaces the dropped connections. The container
  healthcheck uses `/actuator/health/liveness`, which doesn't query the
  database, precisely so it doesn't keep Neon awake around the clock.
  Calling `/actuator/health` (which does check the DB) from an uptime
  monitor every minute would.
- **Supabase** exposes tables in `public` through its auto-generated Data
  API. In a project dedicated to this API, **turn the Data API off**
  (Project Settings → Data API) so the API's tables can't be read with the
  project's public key.

## 2. Point the domain at the server

At your DNS provider, add an **A record**: name `api` (or whatever subdomain
you chose) → the VPS's public IPv4 address. Check **(laptop)**:

```bash
dig +short api.yourdomain.com      # must print the VPS IP
```

Caddy can't get a certificate until this works.

## 3. Prepare the server (once)

Log in as root with the key the provider installed **(laptop)**:

```bash
ssh root@<server-ip>
```

**Create your own user** instead of working as root:

```bash
adduser deploy                        # pick a strong password (used for sudo)
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh
cp ~/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
```

Open a **second terminal** and check `ssh deploy@<server-ip>` works before
continuing — if it doesn't, you can still fix it from the first one.

**Turn off password and root SSH logins** (keys only), as `deploy`:

```bash
sudo tee /etc/ssh/sshd_config.d/99-hardening.conf >/dev/null <<'EOF'
PasswordAuthentication no
PermitRootLogin no
EOF
sudo systemctl restart ssh
```

**Firewall** — allow SSH *first*, or you'll lock yourself out:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp                # HTTP/3
sudo ufw enable
```

Docker bypasses `ufw` for ports a container publishes. That's why
`compose.prod.yaml` publishes only Caddy's 80/443 — never add `ports:` to
`security-api`: it trusts `X-Forwarded-For` from private addresses, so it
must only be reachable through Caddy.

**Swap** — a safety net so a memory spike slows the server down instead of
the kernel killing a process:

```bash
sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Automatic security updates**:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades    # answer "Yes"
```

**Install Docker** (official script; see
<https://docs.docker.com/engine/install/ubuntu/> for the manual way):

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
less get-docker.sh                    # look before you run it
sudo sh get-docker.sh
sudo usermod -aG docker deploy        # run docker without sudo
exit                                  # log out and back in for the group to apply
```

Being in the `docker` group is effectively root on this server — only add
users you'd trust with root. Check: `docker run --rm hello-world`.

## 4. Let the server pull the image

CI (`.github/workflows/ci.yml`, job `image`) builds the image on every push
to `main` after the tests pass, boots it with the `prod` profile against a
TLS Postgres under the same 768 MB limit, and publishes it to GHCR as
`sha-<commit>` (immutable) and `latest`, plus `vX.Y.Z` / `vX.Y` for release
tags. The run summary prints the verified `image@sha256:...` reference.

New GHCR packages are **private**. Either:

- make it public: GitHub → your profile → Packages → `security-api` →
  Package settings → Change visibility (anyone can then pull the compiled
  image; it contains no secrets), **or**
- keep it private and log in once on the server. Create a **classic**
  personal access token with only the `read:packages` scope (GitHub →
  Settings → Developer settings → Personal access tokens), then:

  ```bash
  docker login ghcr.io -u <github-username>    # paste the token when asked
  ```

  The token is stored in `~/.docker/config.json`; it can only read packages.
  Revoke it in GitHub if the server is ever compromised.

## 5. Copy the deploy files to the server

From the repo root **(laptop)**:

```bash
ssh deploy@<server-ip> 'sudo mkdir -p /opt/h-space && sudo chown deploy:deploy /opt/h-space'
rsync -av --exclude .env deploy/ deploy@<server-ip>:/opt/h-space/
```

The exclude makes it safe to re-run later: it never overwrites the server's
`.env`.

## 6. Fill in the settings

```bash
cd /opt/h-space
cp prod.env.example .env
chmod 600 .env                        # only you can read it
nano .env
```

| Variable | Required | What it is |
|---|---|---|
| `API_IMAGE` | yes | `ghcr.io/hen-heang/h-space-api/security-api@sha256:...` from the CI run summary. |
| `API_DOMAIN` | yes | e.g. `api.yourdomain.com` (§2). |
| `ACME_EMAIL` | yes | Let's Encrypt's contact for certificate problems. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | The database endpoint (§1). |
| `JWT_SECRET` | yes | `openssl rand -base64 64 \| tr -d '\n'; echo` |
| `MFA_ENCRYPTION_KEY` | yes | `openssl rand -base64 32` — never change it later (§12). |
| `CORS_ALLOWED_ORIGINS` | yes | The web app's origin(s), e.g. `https://app.yourdomain.com`. |
| `FRONTEND_URL` | recommended | Where links in emails point. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | recommended | SMTP account (Gmail app password). |
| `OWNER_EMAIL` | recommended | Becomes admin once verified. |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE` | no | Default 5 / 1. |
| `API_MEM_LIMIT` | no | Default `768m`; the heap is 60% of it. |
| `SIGNUP_MODE`, `GOOGLE_CLIENT_IDS`, `API_DOCS_ENABLED`, `SUPABASE_*`, `SPRING_FLYWAY_*` | no | See comments in `prod.env.example`. |

Generate secrets **on the server** (they never need to leave it, except
into your password manager). A missing required value stops
`docker compose up` with an error naming it.

**Now save a copy of the finished `.env` in your password manager.**

## 7. First start and health check

```bash
cd /opt/h-space
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps          # security-api "healthy", caddy "running"
docker compose -f compose.prod.yaml logs -f     # Ctrl+C to stop watching
```

On first start the API runs the Flyway migrations against the empty
database, then Caddy requests the certificate. Then **(laptop)**:

```bash
curl https://api.yourdomain.com/actuator/health             # {"status":"UP",...} - includes the DB
curl -I http://api.yourdomain.com/actuator/health           # 308 redirect to https
curl -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/swagger-ui/index.html   # 404
curl -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/actuator/env            # 401
```

Health responses carry only a status — no component details, versions or
configuration. `/actuator/health` checks the database;
`/actuator/health/liveness` (used by the container healthcheck) doesn't.
No other actuator endpoint is exposed.

**Create the admin account**: sign up (web app or API) with the
`OWNER_EMAIL` address and verify the email. That account becomes admin
automatically and can invite others (`POST /api/admin/invites`) — sign-up
is invite-only by default.

**Connect the web app**: point the frontend's API URL at
`https://api.yourdomain.com` and make sure its origin is in
`CORS_ALLOWED_ORIGINS`.

## 8. Database migrations and backups

**Flyway runs automatically at every API start**, before the API accepts
requests. It applies any `V{n}__*.sql` in the new image that the database
hasn't seen yet, in order, and records them in `flyway_schema_history`.
Hibernate only *validates* the schema (`ddl-auto=validate`); it never
changes it. If a migration fails, the API exits, the container restarts and
retries, and the old API is already gone — so check migrations before
deploying (below).

**Backups are your responsibility, through the database provider.** The VPS
holds no data. Before real users:

- Check what your plan includes: Neon offers point-in-time restore within
  a plan-dependent history window; Supabase includes daily backups on paid
  plans. Free tiers have short windows or none.
- Take your own dump before every deploy that contains a migration, and
  periodically. From the server or your laptop (needs a session endpoint —
  Neon direct or Supabase session pooler; use a client at least as new as
  the server):

  ```bash
  read -rs PGPASSWORD; export PGPASSWORD    # paste the DB password (not echoed)
  docker run --rm -e PGPASSWORD -e PGSSLMODE=require -v "$PWD:/out" postgres:17 \
    pg_dump --format=custom --no-owner -h <host> -U <user> -d <database> \
    -f /out/h-space-$(date +%F-%H%M).dump
  unset PGPASSWORD
  ```

  Store dumps off the VPS (its 20 GB disk is not a backup location), and
  treat them as sensitive: they contain password hashes and encrypted TOTP
  secrets.
- **Rehearse a restore** into a scratch database (e.g. a Neon branch) with
  `pg_restore --no-owner -d <scratch-database> <file>.dump`.
- Keep `MFA_ENCRYPTION_KEY` with the backups' credentials: a restore without
  it leaves MFA users unable to sign in.

**Test migrations first** where possible: on Neon, create a branch of the
production database and run the new image against it locally before
deploying.

## 9. Deploy a new version

Every push to `main` produces a new image. Deploy by **digest**: a digest
always means exactly the same image; `latest` moves.

```bash
cd /opt/h-space
grep API_IMAGE .env                 # 1. note the current digest - your rollback
# 2. if the release adds migrations, take a database dump first (§8)
nano .env                           # 3. set API_IMAGE to the new digest
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps
curl -s https://api.yourdomain.com/actuator/health
```

`up -d` pulls the new image and replaces the API container; expect up to a
minute of `502` while it starts (single server, no rolling deploy).

If `compose.prod.yaml` or `Caddyfile` changed in the repo, re-run the
`rsync` from §5 first, then `up -d`.

## 10. Roll back

Put the previous digest back in `API_IMAGE` and run
`docker compose -f compose.prod.yaml up -d`.

**Flyway doesn't undo migrations.** If the release you're undoing added a
migration, the schema has already changed. The old image still starts if
the change was purely additive (new table/column); if a column was renamed
or removed, its schema validation fails and you must also restore the
database from the dump taken in §9 step 2 (or the provider's point-in-time
restore) — losing anything written since. So read a release's migrations
before deploying it.

## 11. Monitoring

All from `/opt/h-space`; `dc` means `docker compose -f compose.prod.yaml`
(add `alias dc='docker compose -f compose.prod.yaml'` to `~/.bashrc`).

| What | Command | Healthy looks like |
|---|---|---|
| Containers | `dc ps` | `security-api` "healthy", `caddy` "running", no recent restart |
| Restarts | `docker inspect -f '{{.RestartCount}}' h-space-security-api-1` | Not increasing |
| Memory per container | `docker stats --no-stream` | API well under 768 MiB (~400 MiB after startup in testing) |
| Server memory | `free -h` | Some "available"; swap mostly unused |
| Disk | `df -h /` and `docker system df` | Under ~80% |
| API logs | `dc logs --tail 200 security-api` | No repeated `ERROR` |
| Caddy logs | `dc logs --tail 100 caddy` | No certificate errors |
| Was the API OOM-killed? | `docker inspect -f '{{.State.OOMKilled}}' h-space-security-api-1` | `false` |

- **Logs are capped**: each container keeps at most 3 × 10 MB of logs.
  Logs don't include tokens or passwords; they can include emails and IPs.
- **Disk**: old images pile up with each deploy. After a deploy has
  succeeded, `docker image prune -a` removes images not in use (keep the
  previous digest noted so you can pull it again to roll back).
- **Uptime**: an external monitor (e.g. a free uptime service) on
  `https://api.yourdomain.com/actuator/health/liveness` every few minutes.
  Use `liveness` on Neon so it doesn't keep the database awake (§1).
- **Reboots**: the server installs security updates automatically; reboot
  when `/var/run/reboot-required` exists (`sudo reboot` — containers come
  back by themselves).

Never run `dc down -v` — `-v` deletes Caddy's certificate volume.

## 12. Changing secrets

After editing `.env`, run `dc up -d` (**not** `restart` — it keeps the old
values).

| Secret | Effect / extra steps |
|---|---|
| `JWT_SECRET` | Every user is signed out (existing tokens stop working). |
| `DB_PASSWORD` | Reset it at the provider first, then update `.env`, then `up -d`. |
| `MAIL_PASSWORD`, `GOOGLE_CLIENT_IDS`, ... | Just update and `up -d`. |
| `MFA_ENCRYPTION_KEY` | **Don't change it.** There's no re-encryption yet; MFA users would be locked out. |

If a secret leaked, change it immediately and follow `SECURITY.md`.

## 13. Troubleshooting

| Symptom | Look at |
|---|---|
| `up` stops with "required variable X is missing" | Add `X` to `.env`. |
| API keeps restarting | `dc logs security-api`. Common causes below. |
| … `The server does not support SSL` | `DB_URL` points at something without TLS — use the provider's endpoint (§1). |
| … `Connection refused` / timeout / `UnknownHost` | Wrong host or port; Supabase direct host on an IPv4-only VPS (use the session pooler). |
| … `password authentication failed` | `DB_USERNAME`/`DB_PASSWORD`; Supabase pooler users are `postgres.<project-ref>`. |
| … `prepared statement "S_1" already exists` / Flyway lock errors | `DB_URL` is a transaction pooler (Neon `-pooler`, Supabase `:6543`). Use a session endpoint (§1). |
| … `Found non-empty schema(s) "public" but no schema history table` | The database isn't empty — use a dedicated database (§1). |
| … `Schema-validation: missing table/column` | Rolled back past a migration (§10). |
| No HTTPS / certificate errors | `dc logs caddy`. Usually DNS not pointing here yet (§2) or ports 80/443 blocked (provider firewall too, not just `ufw`). |
| `502 Bad Gateway` | The API is starting or crashed: `dc ps`, `dc logs --tail 100 security-api`. |
| First request after a quiet period is slow | Neon waking up (§1) — expected. |
| Browser says CORS error | Frontend origin missing from `CORS_ALLOWED_ORIGINS` (exact scheme + host, no trailing slash). |
| Emails don't arrive | `dc logs security-api`, look for mail errors; Gmail needs an app password. |
| Out of memory / very slow | `free -h`, `docker stats`, OOMKilled check (§11). |

## 14. Before real users — checklist

- [ ] SSH key-only, firewall on, swap on, automatic updates on (§3)
- [ ] Dedicated database; Supabase Data API off if Supabase (§1)
- [ ] `https://.../actuator/health` returns `UP`; Swagger 404 (§7)
- [ ] `.env` saved in a password manager
- [ ] Provider backups/PITR checked, a manual dump taken **and** restored
      once into a scratch database (§8)
- [ ] Deployed by digest, previous digest noted
- [ ] Owner account created and MFA enabled on it
- [ ] Uptime monitor on `/actuator/health/liveness` (§11)

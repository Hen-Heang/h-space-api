# Deploying to a VPS

A step-by-step runbook for running `security-api` on one Linux server with
the files in `deploy/`. It assumes no prior server experience; commands
marked **(laptop)** run on your own computer, everything else on the server.

What ends up running (all in Docker, see `deploy/compose.prod.yaml`):

```
internet ──443──> caddy (HTTPS) ──> security-api ──> postgres
                                                        ^
                                         backup (daily pg_dump)
```

Only Caddy is reachable from the internet.

---

## 0. What you need

- **A VPS**: Ubuntu 24.04 LTS, **2 GB RAM** or more (the API alone is
  capped at 768 MB), 1–2 vCPU, 20 GB+ disk. Any provider works.
- **A domain** you control, e.g. `api.yourdomain.com` for the API.
- **An SSH key** on your laptop. Check with `ls ~/.ssh/id_ed25519.pub`;
  create one with `ssh-keygen -t ed25519` if it's missing. Add the public
  key when creating the VPS.
- **A Gmail app password** (or other SMTP account) for sending verification
  and sign-in emails.

## 1. Point the domain at the server

At your DNS provider, add an **A record**: name `api` (or whatever
subdomain you chose) → the VPS's public IPv4 address. Check it from your
laptop **(laptop)**:

```bash
dig +short api.yourdomain.com      # must print the VPS IP
```

DNS can take minutes to hours to update. Caddy can't get a certificate
until this works.

## 2. Prepare the server (once)

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

Note: Docker bypasses `ufw` for ports a container publishes. That's why
`compose.prod.yaml` publishes only Caddy's 80/443 — never add `ports:` to
`postgres` or `security-api`.

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
users you'd trust with root.

Check: `docker run --rm hello-world`.

## 3. Let the server pull the image

CI publishes the image to GitHub Container Registry (GHCR), and new packages
are **private**. Either:

- make it public: GitHub → your profile → Packages → `security-api` →
  Package settings → Change visibility (the code is still yours; anyone can
  pull the compiled image), **or**
- keep it private and log in once on the server. Create a **classic**
  personal access token with only the `read:packages` scope (GitHub →
  Settings → Developer settings → Personal access tokens), then:

  ```bash
  docker login ghcr.io -u <github-username>    # paste the token as the password
  ```

## 4. Copy the deploy files to the server

From the repo root **(laptop)**:

```bash
ssh deploy@<server-ip> 'sudo mkdir -p /opt/h-space && sudo chown deploy:deploy /opt/h-space'
rsync -av --exclude backups --exclude .env deploy/ deploy@<server-ip>:/opt/h-space/
```

The excludes make it safe to re-run later: they never overwrite the
server's `.env` or its backups.

## 5. Fill in the settings

```bash
cd /opt/h-space
cp prod.env.example .env
chmod 600 .env                        # only you can read it
nano .env
```

Each line in `prod.env.example` explains itself. Generate the secrets on the
server:

```bash
openssl rand -base64 24                        # POSTGRES_PASSWORD
openssl rand -base64 64 | tr -d '\n'; echo     # JWT_SECRET
openssl rand -base64 32                        # MFA_ENCRYPTION_KEY
```

For `API_IMAGE`, open the latest successful CI run on `main` in GitHub
(Actions → CI → the run → Summary). It shows
`Verified image: ghcr.io/hen-heang/h-space-api/security-api@sha256:...` —
copy that whole reference. A digest always means exactly the same image; a
tag like `latest` can move.

**Now save a copy of the finished `.env` in your password manager.**
Without `MFA_ENCRYPTION_KEY`, backups can't be fully restored (see §9).

## 6. First start

```bash
cd /opt/h-space
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps          # all should become "healthy"/"running"
docker compose -f compose.prod.yaml logs -f     # Ctrl+C to stop watching
```

The first start takes a minute or two: Postgres initialises, the API runs
the Flyway migrations, then Caddy requests the certificate. Then **(laptop)**:

```bash
curl https://api.yourdomain.com/actuator/health     # {"status":"UP"}
```

**Create the admin account**: sign up (from the web app or the API) with
the address you set as `OWNER_EMAIL`, and verify the email. That account
becomes admin automatically and can then invite others
(`POST /api/admin/invites`) — sign-up is invite-only by default.

**Connect the web app**: point the frontend's API URL at
`https://api.yourdomain.com`, and make sure its origin is listed in
`CORS_ALLOWED_ORIGINS`.

## 7. Deploy a new version

Every push to `main` produces a new image (after the tests pass).

```bash
cd /opt/h-space
# 1. backup first
docker compose -f compose.prod.yaml exec backup sh /scripts/backup.sh once
# 2. set API_IMAGE in .env to the new digest from the CI run summary
nano .env
# 3. switch
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps
curl -s https://api.yourdomain.com/actuator/health
```

Note the old digest before editing — it's your rollback.

If `compose.prod.yaml` or `Caddyfile` changed in the repo, re-run the
`rsync` from §4 first, then `up -d`.

## 8. Roll back

Put the previous digest back in `API_IMAGE` and run
`docker compose -f compose.prod.yaml up -d`.

**Watch out for database migrations.** If the release you're undoing added a
`V{n}__*.sql` migration, the schema has already changed. The old version
still starts if the change was purely additive (new table/column), but if a
column was renamed or removed it won't, and you also need to restore the
backup from step 7.1 (§9) — losing anything written since. So: read the
release's migrations before deploying it.

## 9. Backups and restore

Covered in `docs/build-and-run.md` → "Backups and restore". In short:
daily dumps land in `/opt/h-space/backups`, kept 14 days;
`./restore.sh backups/<file>.dump` restores one. Two rules:

- **Copy them off the server** regularly **(laptop)**:
  `rsync -av deploy@<server-ip>:/opt/h-space/backups/ ~/h-space-backups/`
- **Keep `.env` in your password manager** — restoring with a different
  `MFA_ENCRYPTION_KEY` locks out every MFA user.

## 10. Day-to-day commands

All from `/opt/h-space`; `dc` here means
`docker compose -f compose.prod.yaml` (add
`alias dc='docker compose -f compose.prod.yaml'` to `~/.bashrc`).

| Task | Command |
|---|---|
| What's running | `dc ps` |
| API logs (follow) | `dc logs -f security-api` |
| Last backup result | `dc logs --tail 5 backup` |
| Restart the API | `dc restart security-api` |
| Apply a `.env` change | `dc up -d` (**not** `restart` — it keeps the old values) |
| Stop everything (keeps data) | `dc down` |
| Disk usage | `df -h` and `docker system df` |
| Remove old images | `docker image prune -a` (after a deploy succeeded) |

Never run `dc down -v` — `-v` deletes the database and certificate volumes.

The server installs security updates automatically; reboot when
`/var/run/reboot-required` exists (`sudo reboot` — the containers come back
by themselves).

## 11. Changing secrets

After editing `.env`, run `dc up -d`.

| Secret | Effect / extra steps |
|---|---|
| `JWT_SECRET` | Every user is signed out (existing tokens stop working). |
| `MAIL_PASSWORD`, `GOOGLE_CLIENT_IDS`, ... | Just update and `up -d`. |
| `POSTGRES_PASSWORD` | Postgres only reads this on its very first start. Change it in the database **first**: `dc exec postgres psql -U postgres -c "ALTER USER postgres PASSWORD 'new-password'"`, then update `.env`, then `up -d`. |
| `MFA_ENCRYPTION_KEY` | **Don't change it.** There's no re-encryption yet; MFA users would be locked out. |

If a secret leaked, change it immediately and follow `SECURITY.md`.

## 12. Troubleshooting

| Symptom | Look at |
|---|---|
| `up` stops with "required variable X is missing" | Add `X` to `.env`. |
| No HTTPS / certificate errors | `dc logs caddy`. Usually DNS not pointing here yet (§1) or ports 80/443 blocked (provider firewall too, not just `ufw`). |
| `502 Bad Gateway` | The API is starting or crashed: `dc ps`, `dc logs --tail 100 security-api`. |
| API keeps restarting | `dc logs security-api` — usually a bad secret (`JWT_SECRET`/`MFA_ENCRYPTION_KEY` not valid Base64) or the DB password mismatch (§11). |
| Browser says CORS error | Frontend origin missing from `CORS_ALLOWED_ORIGINS` (exact scheme + host, no trailing slash). |
| Emails don't arrive | `dc logs security-api`, look for mail errors; check `MAIL_USERNAME`/`MAIL_PASSWORD` (Gmail needs an app password). |
| Out of memory / very slow | `free -h`, `docker stats`. Use a 2 GB+ server. |

## 13. Before real users — checklist

- [ ] SSH key-only, firewall on, automatic updates on (§2)
- [ ] `https://.../actuator/health` returns `UP`
- [ ] `.env` saved in a password manager
- [ ] A backup copied off the server, **and** restored once as a test
- [ ] Deployed by digest, previous digest noted
- [ ] Owner account created and MFA enabled on it
- [ ] `https://.../swagger-ui/index.html` returns 404 (API docs are off in
      `prod`; `API_DOCS_ENABLED=true` turns them on temporarily)

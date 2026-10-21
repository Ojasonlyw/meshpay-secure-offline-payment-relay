# Automatic deployment

Every push or merged PR to `main` runs backend tests and the complete frontend verification workflow. Only when both pass does CD build the exact commit, publish `ghcr.io/ojasonlyw/meshpay-secure-offline-payment-relay:sha-<commit>`, and deploy its `@sha256:...` digest over SSH. No release tag or manual deployment is required. Feature branches and PRs run CI without deployment credentials.

This repository is an educational demo with open administrative APIs. The deployment binds HTTP to server loopback and enables the `postgres` profile, which disables demo sending/reset and seeded demo accounts/devices. Use an SSH tunnel or private network to view it; public hosting needs a separately designed access-control layer. The `production` name is a GitHub environment name, not a claim of payment readiness.

## One-time server setup

Use a dedicated Linux x86-64 host with Docker Engine, Compose v2 (`--wait` support), OpenSSH, Bash, GNU coreutils, and `flock`. Docker access grants substantial host control: use a dedicated deploy user/host. Allow the GitHub runner to reach SSH. There are no public database or Redis ports.

1. As an administrator, create the deployment directory owned by the deploy user:

   ```sh
   sudo install -d -m 700 -o meshpay -g meshpay /opt/meshpay
   ```

2. Copy `deploy/production.env.example` to `/opt/meshpay/.env` on the host, set a random password, and restrict permissions with `chmod 600 /opt/meshpay/.env`. Use a fresh database volume; do not attach an old local development database. Changing this password later does not rotate an existing PostgreSQL role.
3. Add a dedicated SSH public key to that user's `~/.ssh/authorized_keys`; keep its private key only in the GitHub environment secret below.
4. For a private GHCR package, log in **on the server as the deploy user** with a token having `read:packages` and access to this repository's package. The publishing job uses its short-lived `GITHUB_TOKEN`; the server uses this separate read-only credential. For example, with `GHCR_READ_TOKEN` already set securely:

   ```sh
   printf '%s' "$GHCR_READ_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
   unset GHCR_READ_TOKEN
   ```

   Restrict the Docker credential file to the deploy user, or configure a Docker credential helper. Public images do not require server login.

## One-time GitHub setup

In repository **Settings → Environments**, create `production` and restrict its deployment branches to `main`. Leave required reviewers disabled if you want deployments to proceed unattended. Protect `main` with PR checks appropriate to your repository policy. Enable GitHub Actions and GHCR package publishing; if the package already exists, grant this repository Actions access in package settings.

Set these **environment secrets**:

| Secret | Value |
| --- | --- |
| `DEPLOY_HOST` | Server DNS name or IPv4 address, without `https://` |
| `DEPLOY_USER` | Dedicated Linux deployment username |
| `DEPLOY_SSH_KEY` | Entire dedicated SSH private key without a passphrase |
| `DEPLOY_KNOWN_HOSTS` | Verified OpenSSH known_hosts line(s) for this server |

Obtain the SSH host public key through the provider console or another trusted channel. Compare its fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the host. The secret contains `hostname ssh-ed25519 AAAA...`, or `[hostname]:port ssh-ed25519 AAAA...` for a non-default port. Do not trust an unchecked `ssh-keyscan` result.

Optional **environment variables**: `DEPLOY_PORT` (default `22`) and `DEPLOY_PATH` (default `/opt/meshpay`). The path must be an absolute path without spaces or `..`. This workflow targets `main`; if you rename the deployment branch, update the branch rules in all three workflows.

The current checkout is on `frontend-refactor`. Commit the project changes and merge them into `main` to activate the pipeline. These local edits do not configure GitHub secrets or provision a server automatically.

## Release behavior and operations

Each run uploads its own Compose file and deployment script into `releases/<commit>-<run>-<attempt>`. Both GitHub concurrency and a host lock serialize deployment. The host validates configuration, pulls by digest, starts healthy PostgreSQL/Redis, takes a `pg_dump` snapshot, and then starts the app. Container readiness requires Spring readiness plus successful PostgreSQL and Redis checks. A dashboard page returning 200 alone is not the health signal.

The app runs as UID 10001 with dropped capabilities. Flyway applies migrations at startup. The deployment promotes `current` only after the exact image is healthy. If application startup/readiness fails, it tries the previous release and still fails the workflow. No automatic database restore is performed. Use additive, backward-compatible migrations; a destructive migration may prevent application rollback. This single-host deployment has a brief restart window, not zero downtime.

Backups live under `/opt/meshpay/backups` with restrictive permissions. Copy them to durable storage and define retention/restore drills on the host; the pipeline does not delete old backups, releases, or images. Watch disk usage. Database volumes survive application replacement. Redis is disposable and unpersisted; the payment database remains authoritative.

To view the deployed site privately:

```sh
ssh -L 18080:127.0.0.1:8080 meshpay@YOUR_SERVER
# Open http://localhost:18080 on your computer.
```

The demo generates a new server RSA key on every application startup and stores virtual mesh custody in memory. Drain pending mesh packets before releases; packets encrypted for the previous process cannot be decrypted afterward. Durable key management and custody persistence remain separate application work.

To retry, use **Actions → MeshPay CD → Run workflow → main**, or push the next fix. To roll back on the host:

```sh
bash /opt/meshpay/previous/deploy.sh /opt/meshpay
```

This reuses the previous digest and Compose file, takes another snapshot, and verifies health. Never use `docker compose down -v` for deployment. After a failed rollback, investigate host health and schema compatibility before using a database backup.

References: [GitHub reusable workflows](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows), [publishing to GHCR](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images), [Compose health dependencies](https://docs.docker.com/compose/how-tos/startup-order/).

## Local verification

Validated on 2026-10-20: all 88 backend tests passed; actionlint and ShellCheck passed; six deployment tests covered successful promotion, initial deployment, rejected mutable references, immutable release metadata, failed backups, and unhealthy-release rollback. The real Dockerfile built successfully, and production Compose started on an isolated project/port with UID 10001. Dashboard/readiness returned HTTP 200, readiness returned HTTP 503 with Redis stopped, and returned HTTP 200 after recovery. Frontend workflow formatting also passed. Remote SSH, GHCR push, and the hosted Actions run require the one-time environment setup above and have not been exercised locally.

# MeshPay

> Local educational demo only. No authentication, no real payments, and no public exposure.

MeshPay is a Spring Boot demo of signed and encrypted offline payment packets relayed through virtual mesh devices and settled later by a bridge. PostgreSQL is authoritative; Redis provides disposable idempotency state. It is not UPI, NPCI, a bank integration, or production payment software.

## Current status

The security and reliability work is implemented locally but has not been pushed to GitHub. The current checkout is `frontend-refactor`; GitHub `main` does not contain these changes yet.

Verified locally on 2026-10-20:

- 88 backend tests passed.
- 20 frontend tests, lint, and formatting passed; browser checks previously passed (9 tests).
- Docker image build, Compose readiness, actionlint, ShellCheck, and 6 deployment tests passed.
- Readiness returns 503 when Redis is unavailable and 200 after recovery.

See [SECURITY_FIX_REPORT.md](SECURITY_FIX_REPORT.md) for the short implementation report and accepted limits.

## Stack

Java 17, Spring Boot 3.5.16, PostgreSQL 16, Flyway, Redis 7, Spring Data JPA, Thymeleaf, Ed25519 signatures, and RSA-OAEP/AES-GCM packet encryption.

The demo includes bounded amounts and requests, safe framework errors, atomic Redis claims with database recovery, settlement-time device revocation, bounded virtual-device custody, capped reads, and trace IDs. It has no login, ownership checks, roles, rate limits, or durable server keys/mesh custody. Demo signing/reset helpers are profile-restricted.

## Run locally (Windows)

Requirements: JDK 17, Docker Desktop, and Compose v2.

```powershell
cd "C:\Users\ASUS\Downloads\UPI_Without_Internet-main\UPI_Without_Internet-main"
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Open <http://localhost:8080>. Stop the app with `Ctrl+C`; stop local services with `docker compose down`.

On Linux/macOS, use `./mvnw spring-boot:run`.

## Automatic delivery

`.github/workflows/cd.yml` runs on pushes to `main`. It calls backend CI and frontend verification, builds a Java 17 non-root image, publishes the commit image to GHCR, and deploys its SHA-256 digest through the protected `production` environment. A failed readiness check attempts the previous release after taking a PostgreSQL backup.

Complete the one-time server and GitHub secret setup in [deploy/README.md](deploy/README.md). The deployment files are [Dockerfile](Dockerfile), [compose.production.yaml](compose.production.yaml), and [deploy/](deploy/). The engineering brief is [CD_IMPLEMENTATION_PROMPT.md](CD_IMPLEMENTATION_PROMPT.md).

The deployment is private by default: HTTP binds to server loopback, databases are internal Compose services, and access should use an SSH tunnel or private network. Remote SSH, GHCR publishing, and hosted Actions have not been exercised from this workstation because no deployment host or GitHub secrets are configured.

## Useful checks

```powershell
.\mvnw.cmd -B test
docker build -t meshpay-local .
docker compose -f compose.production.yaml config --quiet
```

Never commit `.env` or deployment credentials. Use additive Flyway migrations; an application rollback cannot automatically reverse a database migration.

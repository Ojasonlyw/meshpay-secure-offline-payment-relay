# MeshPay CD implementation brief

Maintain automatic delivery for this Java 17/Spring Boot demo.

## Required flow

Push to `main` → backend CI and frontend checks → build a non-root image → publish the commit image to GHCR → deploy its immutable digest through the protected `production` environment → verify readiness.

## Rules

- Keep CI and frontend workflows reusable; failures must block publishing.
- Pin GitHub Actions and container bases to reviewed SHAs/digests.
- Never expose deployment secrets to pull requests.
- Use PostgreSQL as the source of truth and Redis as disposable cache state.
- Deploy with private Compose networking, loopback HTTP, persistent PostgreSQL storage, and the `postgres` profile.
- Validate SSH host keys; use a dedicated deploy user and read-only registry access.
- Back up PostgreSQL before Flyway migrations, serialize releases, and attempt the previous release on failed readiness.
- Never reverse migrations automatically, delete volumes, or use mutable `latest` tags.
- Document that this demo has no authentication and loses generated server keys/in-memory mesh custody on restart.

## Acceptance checks

Run actionlint, ShellCheck, deployment unit tests, Maven tests, Docker build, Compose validation, and a real readiness smoke test. Report remote setup separately; do not claim deployment is active until GitHub secrets, the host, and a successful Actions run are verified.

# MeshPay implementation report

Status: changes are local on `frontend-refactor` and have not been pushed to GitHub. This is a limited update to a local educational demo, not a security audit or production approval.

## Implemented

- Safe 404/405/415 responses preserve `Allow` and trace IDs; unexpected failures remain safe 500s.
- Amounts use exact decimal validation: positive values, at most two decimals, maximum `1,000,000.00`, and no exponent/rounding.
- Signature and freshness checks happen before payment identity reservation and route linkage.
- Redis claims use atomic Lua ownership tokens, leases, terminal retention, expiry-index cleanup, and PostgreSQL recovery.
- Settlement rechecks device/key status under locks using device → key → payment order. Revoked devices cannot settle.
- V15 stores nullable verified-key fingerprints for legacy evidence.
- Requests, DTO fields, TTLs, virtual-device custody, list reads, and dashboard index state are bounded.
- Demo signing/reset routes are profile-restricted; default HTTP binding is loopback; credentials come from the environment.
- Logs omit raw request bodies, exception messages, SQL, and secrets.
- CI action references and production container bases are pinned to reviewed digests.
- CD now verifies CI, builds GHCR images by commit, deploys by digest, backs up PostgreSQL, checks readiness, and attempts the previous release on failure.

## Verification

Verified locally on 2026-10-20:

| Check | Result |
| --- | --- |
| Maven backend suite | 88 passed, 0 failed |
| Frontend unit checks | 20 passed |
| Frontend lint/format | Passed |
| Browser checks | 9 passed previously |
| Docker image build | Passed |
| Compose readiness | 200 when healthy; 503 during Redis outage |
| Actionlint / ShellCheck | Passed |
| Deployment tests | 6 passed: first deploy, promotion, backup failure, rollback, mutable-tag rejection, metadata reuse rejection |

## Remaining limits

The demo has no authentication, ownership, roles, rate limits, or public-facing access control. Device registration, trust changes, and revocation remain open demo controls. Generated server RSA keys and in-memory mesh custody are lost on restart. PostgreSQL is authoritative; Redis is disposable. Use additive migrations because application rollback does not reverse schema changes.

The GHCR/SSH deployment has not run from GitHub because no host or GitHub environment secrets are configured. Complete [deploy/README.md](deploy/README.md), then merge the local changes into `main` to activate [CD](.github/workflows/cd.yml).

The development-only npm advisory chain through Stylelint remains documented; `npm audit fix --force` was not used.

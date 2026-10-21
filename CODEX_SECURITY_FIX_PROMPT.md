# Codex Implementation Prompt — MeshPay Demo Security and Reliability Fixes

## Role and objective

Act as a careful senior Java/Spring maintainer. Improve this repository as a **local educational Spring Boot REST demo with simulated payments**.

This task is an implementation task, not a security-marketing exercise. Work from evidence in the repository, existing tests, and the supplied audit plan. Distinguish confirmed defects from assumptions, optional improvements, and accepted limitations.

The goal is to fix real bugs and minor reliability/security issues without redesigning the application.

## Hard boundaries

Do **not** add or implement:

- authentication or login
- Spring Security, JWT, sessions, roles, owner/admin checks, or object-level authorization
- CSRF/CORS controls as part of an authentication model
- per-user or per-owner quotas or rate limits
- real bank, UPI, NPCI, PSP, or real-money integrations
- Kafka, microservices, Kubernetes, or a second cache

The open device-trust boundary is an explicitly accepted limitation of this local demo. Document it honestly; do not pretend it is fixed. Keep the application clearly marked as demo-only and unsafe for public exposure.

Redis remains the idempotency/cache layer. PostgreSQL remains the source of truth. Do not weaken existing uniqueness, ledger, optimistic-locking, or integrity constraints.

Do not modify existing Flyway migrations V1–V14. Any required schema change must use a new migration V15 or later.

Do not run `npm audit fix --force`, and do not make a dependency change solely to hide an audit warning.

## Working method

1. Read the source, tests, configuration, README, and current audit documents before editing.
2. Inspect the current implementation rather than assuming the line references in the plan are still exact.
3. Work in the order below, one issue at a time.
4. For each issue, make the smallest safe change, add or update regression tests, and run the relevant checks before continuing.
5. Preserve public API response shapes unless a change is necessary and documented.
6. Do not delete tests, lower assertions, suppress warnings, or change constraints to make the build pass.
7. If the proposed fix conflicts with current behavior, stop and explain the conflict in the final report; do not silently choose a behavior.
8. Do not commit changes. Leave the working tree available for review.

## Test commands

Use the repository's actual build tooling and report exact results. The baseline commands are:

```text
mvn -o -B test "-Dupi.mesh.demo-key-dir=target/audit-demo-keys" "-Dapi.version=1.44"
```

From `frontend-tools`:

```text
npm test
npm run lint
npm run format:check
```

If offline Maven execution or another check is unavailable, state that precisely. Never report an unrun check as passed.

## Required fixes

### 1. Preserve correct HTTP error responses

Inspect `GlobalExceptionHandler` and the actual Spring Boot version in use. The generic exception handler must not turn framework client errors into HTTP 500.

Add narrow handlers for the relevant framework exceptions so that:

- missing resources return 404
- unsupported methods return 405 and preserve `Allow` where provided
- unsupported media types return 415
- validation/domain failures retain their existing mappings
- unexpected failures still return a safe 500

Preserve the existing `ApiErrorResponse` fields and `X-Trace-Id` behavior if present. Do not expose raw exception messages, stack traces, SQL, filesystem paths, or secrets.

Add real Boot-context MockMvc tests for status, headers, body shape, trace ID, safe messages, and the unexpected-error fallback.

### 2. Fix amount precision at the API boundary

Inspect browser amount handling and backend DTOs. Never use floating-point values for ledger writes.

Prefer a clear, conservative contract: validate exact decimal amounts with at most two fractional digits, enforce a documented maximum, and use `BigDecimal` server-side. If the JSON contract already uses strings, preserve it; if the contract must change, update frontend validators and tests together.

Cover zero/cent values, normal values, maximum boundary, excessive scale, exponent notation, very large values, invalid text, and negative values.

Do not silently round user input.

### 3. Verify authenticity before reserving canonical payment identity

Inspect `BridgeIngestionService`, `PaymentLifecycleService`, `DeviceSignatureService`, and `MeshTopologyService`.

The audit reports that a bad-signature envelope can reserve a payment ID/nonce before authenticity is established. Confirm this behavior with a test before changing it.

Where compatible with the existing transaction design:

1. decrypt and validate the envelope/instruction;
2. check freshness;
3. verify the signature using the existing canonicalization logic;
4. reserve the canonical payment identity in PostgreSQL;
5. persist verification evidence;
6. link route metadata;
7. settle.

Do not duplicate canonicalization. Do not remove uniqueness constraints. Do not link routes to canonical payments before successful verification. Authenticated duplicates must still settle at most once. Invalid traffic must not create canonical payment rows.

If transaction boundaries make this unsafe to change in one patch, document the limitation and add a regression test instead of making a speculative partial fix.

### 4. Make Redis idempotency claims atomic and recoverable

Inspect `IdempotencyService`, `BridgeIngestionService`, and current Redis configuration.

Replace any GET-then-SET claim race with an atomic Redis operation, such as a Lua script, that:

- admits only absent or retryable keys;
- writes `PROCESSING` with a random attempt token;
- uses a short configurable processing lease;
- allows only the matching token to write terminal results;
- retains terminal results for a separate longer replay-retention period.

PostgreSQL remains authoritative. If Redis is missing, expired, stale, or unavailable, check the payment row and repair or safely resume using existing database locking. Never create a new payment ID during recovery and never allow Redis loss to cause a second settlement.

Maintain compatibility with old disposable Redis values where practical, or document that the cache can be cleared safely.

Add tests for concurrent claims, stale-token writes, lease expiry, missing cache with an already-settled database row, retryable failure, Redis failure, and duplicate delivery.

### 5. Choose and document revocation timing

The audit reports a race between signature verification, device revocation, and settlement. Use the default policy unless the existing design makes it unsafe:

**Settlement-time revocation:** a device revoked before settlement commits must not settle.

Re-check the device/key under a lock in the settlement transaction, using a documented lock order shared with revocation. Mark the payment rejected without changing balances when the key is revoked.

If acceptance-time semantics are demonstrably more compatible, use that instead, document the choice in `README.md`, and test it explicitly. Do not imply that this adds authorization; who may call revocation remains out of scope.

If signature evidence needs a key fingerprint/version, add a new migration only. Do not break existing relationships without a migration and compatibility tests.

Add barrier-controlled tests for verify/revoke/settle interleavings and check for deadlocks.

### 6. Add bounded resource handling without user-specific controls

Implement simple, global/local-demo controls only:

- HTTP/body size limits that reject oversized requests before expensive work where possible;
- DTO field and TTL bounds matching service limits;
- multipart disabled if the application does not use it;
- bounded packets per virtual device;
- expiry and removal of delivered/expired packets;
- bounded rejected/audit retention;
- pagination or maximum limits for growing list endpoints;
- no full Redis namespace scan on every dashboard poll.

An IP-agnostic global throttle is optional. Do not add per-user limits.

Add tests proving oversized requests return 413 or the documented framework response, validation returns 400, simulator memory is bounded, expired packets are removed, and list endpoints respect limits.

### 7. Fix measured read-performance issues only

Inspect `PaymentQueryService` and `DeviceService` for the reported N+1 queries.

Use a join/projection or one batched lookup for recent-payment transaction data. Batch-load active device keys. Do not make unrelated associations eager and do not optimize unmeasured code.

Preserve response fields and ordering. Reuse the repository's SQL counter tests to prove the query count improves.

### 8. Improve simulator and audit integrity where low-risk

Only if confirmed by the current code:

- use one consistent synchronization policy for virtual-device packet hold/clear/reset;
- avoid independently updating related packet/hop maps;
- preserve or improve signature evidence without breaking existing one-row relationships;
- correct inaccurate settlement comments;
- document that distinct payments can still encounter optimistic-lock conflicts and may need retry;
- document that a failed delivery outcome may use HTTP 200 when that is the established contract, clearly distinguishing retryable infrastructure failure from final rejection.

Add focused concurrency or contract tests. Do not broaden this into a redesign.

### 9. Dependency and supply-chain maintenance

Determine current versions from the repository and authoritative package metadata. Upgrade the Spring Boot parent and compatible transitive Spring/Tomcat versions only when the resulting version is verifiable and compatible with the project.

For npm, investigate the reported `braces` advisory chain through development tooling. Update to a compatible fixed graph if one exists. If not, leave it in place and document the exact reason and exposure. Treat development-only findings separately from production runtime dependencies.

Optionally add Dependabot configuration if it fits the existing repository conventions. Do not claim a complete dependency security audit without running one.

### 10. Demo configuration and documentation

- Bind the local/demo profile to loopback by default where this does not break the documented Docker workflow.
- Keep demo signing/reset helpers profile-restricted and clearly labelled.
- Move non-demo credentials to environment variables; do not commit real secrets.
- Review logs for request payloads, keys, credentials, and exception leakage.
- Pin GitHub Actions to commit SHAs only if this can be done without guessing and the workflow remains functional.
- Document that the server key and in-memory mesh packets are lost on restart unless persistence is actually implemented.
- Keep a prominent warning: local demo, no authentication, no real payments, do not expose publicly.

Do not implement persistent keys or packet custody unless explicitly requested; documentation is the preferred fix for that optional issue.

### 11. Repair stale test preservation checks and README counts

Inspect `frontend-tools/tests/preservation.test.mjs`. If it freezes backend files to an old commit and therefore rejects legitimate backend fixes, replace only that brittle assertion with behavior/API checks. Keep vendor/reference-asset protections.

Update README test counts and claims to match actual post-change results. Do not preserve historical numbers as if they were current.

## Acceptance criteria

The work is acceptable only if:

- existing database integrity constraints remain intact;
- no authentication or real-payment feature was added;
- every changed behavior has a focused regression test;
- HTTP errors, precision, idempotency, revocation policy, limits, and query-count changes are verified;
- old tests still pass unless a test was demonstrably stale, in which case the replacement is explained;
- dependency changes are justified by actual resolved versions and test results;
- documentation accurately states the remaining no-auth/demo limitations;
- the final report separates confirmed fixes, accepted risks, unverified items, and optional work.

## Required final report

Return:

1. Files changed and why.
2. Fixes completed, with the test proving each one.
3. Fixes not made and the reason.
4. Commands executed and exact pass/fail/blocked results.
5. Dependency versions before/after and unresolved advisories.
6. Remaining risks, especially the intentionally open/no-auth demo boundary.
7. Any behavior or API contract changes requiring review.

Use honest language. Do not say “secure,” “production-ready,” “fully audited,” or “vulnerability-free.” The correct conclusion is only: **the local educational Spring REST demo was improved within the explicitly limited scope**.

# MeshPay

Spring Boot backend for a simulated offline mesh payment demo. A sender creates a signed, encrypted payment packet, virtual devices relay it through a mesh, and a bridge uploads it for deferred settlement in the demo ledger.

This is a backend engineering demo. It is not a real UPI, NPCI, or bank integration.

Phase 0 established reproducible setup, CI, integration coverage, and local measurements. The frontend refactor preserves the dashboard's dark/orange Manrope design while correcting demo copy and separating browser modules, ordered CSS and Thymeleaf fragments. Backend source and migrations remain unchanged.

## Supported Flow And Status Semantics

1. Create a payment instruction signed by the registered sender device (server-held demo keys simulate the device).
2. Encrypt the signed instruction into a mesh packet using the server public key.
3. Relay encrypted packet copies through virtual devices using directed topology and explicit gossip rounds.
4. An internet-capable bridge uploads held packets when flush is requested.
5. The server decrypts the packet and validates its instruction and freshness.
6. Verify the registered sender-device signature and device authorization.
7. Enforce idempotency and duplicate protection before allowing settlement.
8. Commit one debit and one credit in the demo ledger with the payment/transaction records.
9. Read the updated account balances after successful settlement.

This is the conceptual flow. In the implementation, envelope validation and the Redis processing claim happen **before decryption**; database payment identity checks occur during registration and again under the settlement lock. Signature verification precedes settlement. Balances and ledger entries commit in the same database transaction.

| Stage | Meaning | What it does not guarantee |
|---|---|---|
| Packet submitted | Created or injected into the simulated mesh | Delivery or settlement; a packet can remain undelivered |
| Packet delivered | Reached the bridge/server ingestion path | Acceptance; delivery can be rejected, failed, expired, or duplicate |
| Payment settled | Server verification succeeded and debit/credit ledger entries committed | Any real-world movement of bank funds |

Offline relay is **not settlement**. Being held at a bridge means ready for upload, not yet processed. Persisted payment lifecycle records begin at server ingestion, not mesh injection. `PENDING` and `PROCESSING` are server-side states; `DUPLICATE` is a delivery outcome, not a payment status. Some malformed packets fail before a payment record exists.

Virtual devices and mesh gossip are simulated server-side behavior, not Bluetooth networking. Signing is demo key handling, not production signing. Seeded accounts and balances are fixtures. The ledger is a transactional demonstration, not banking infrastructure.

## Current Stack

- Java 17
- Spring Boot 3.3.5
- PostgreSQL
- Flyway migrations
- Redis idempotency
- Spring Data JPA
- Thymeleaf dashboard
- Testcontainers integration tests
- Hybrid encryption: RSA-OAEP + AES-256-GCM
- Device authorization: Ed25519 signatures

## What It Demonstrates

- Offline packet creation and mesh forwarding
- Encrypted payment payloads
- Registered-device signatures checked before settlement
- Database-backed directed mesh topology and packet route history
- Redis-backed idempotency for duplicate bridge delivery
- PostgreSQL-backed payment lifecycle
- Transactional settlement
- Double-entry ledger
- Materialized account balances
- Balance reconciliation from ledger history
- Flyway-owned schema management
- Safe API errors with trace IDs

## Architecture

```text
Dashboard / REST Client
        |
        +--> Demo payment packet
        |
        v
Virtual mesh devices
        |
        v
Bridge ingest API
        |
        v
Redis idempotency claim
        |
        v
Decrypt + validate packet
        |
        v
Payment lifecycle
        |
        v
Verify registered sender-device signature
        |
        v
Settlement transaction
        |
        +--> Account balance update
        +--> Transaction record
        +--> Ledger debit entry
        +--> Ledger credit entry
```

## Payment Model

`Payment` represents the lifecycle of a payment request.

Supported statuses:

- `PENDING`
- `PROCESSING`
- `SETTLED`
- `REJECTED`
- `FAILED`
- `EXPIRED`

`Transaction` is the settlement/audit record for a processed payment.

`LedgerEntry` records movement within the simulated ledger:

- sender `DEBIT`
- receiver `CREDIT`

Every settled payment must have exactly two ledger entries.

## Ledger And Reconciliation

`Account.balance` is kept as a fast materialized balance.

The ledger is used for audit and reconciliation:

```text
calculated balance = opening balance + credits - debits
```

`GET /api/reconciliation` compares stored balances with ledger-derived balances and reports mismatches.

Database protections include:

- immutable ledger entries
- immutable opening balances
- one debit and one credit per settled payment
- no ledger entries for rejected, failed, pending, processing, or expired payments

## Database

PostgreSQL is the database engine for dev, test, and the production-style configuration profile. That profile does not make this demo production-ready.

Flyway owns schema creation. Hibernate only validates mappings.

Important settings:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Migrations:

```text
V1  create accounts
V2  create transactions
V3  create ledger
V4  create devices
V5  add indexes
V6  seed demo accounts, dev/test only
V7  create payments
V8  replace ledger with ledger_entries
V9  link transactions to payments
V10 harden double-entry ledger
V11 upgrade devices; create users and device keys
V12 create payment signature audit and nonce uniqueness
V13 create mesh connections and packet routes
V14 seed demo devices and directed topology, dev/test only
```

Production loads only:

```text
classpath:db/migration
```

Dev and test also load:

```text
classpath:db/demo-migration
```

## Run Locally

Requirements: JDK **17**, Docker with Linux containers and Compose v2, and network access on the first run for Maven dependencies/container images. No global Maven installation is needed. Check `java -version` and `docker info` first. Run commands from the directory containing `pom.xml` and `compose.yaml`.

Start PostgreSQL and Redis:

```powershell
docker compose up -d --wait
```

Run the app on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```sh
./mvnw spring-boot:run
```

The wrappers pin Maven 3.9.9, verify the wrapper JAR SHA-256 before execution, and verify newly downloaded Maven distributions against the pinned SHA-256. Unix requires `curl` or `wget` and `sha256sum` or `shasum`. If an interrupted download causes a wrapper checksum error, remove only `.mvn/wrapper/maven-wrapper.jar` and retry.

Open:

```text
http://localhost:8080
```

Default local database:

```text
jdbc:postgresql://localhost:5432/upi_mesh
username: upimesh_dev
password: upimesh_dev
```

Override with:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

Compose binds PostgreSQL to `127.0.0.1:5432` and Redis to `127.0.0.1:6379` by default. If those ports are occupied, export `POSTGRES_PORT` and `REDIS_PORT` in the **same shell** before starting both Compose and the app:

```powershell
$env:POSTGRES_PORT='55432'
$env:REDIS_PORT='6380'
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

On Unix, use `export POSTGRES_PORT=55432 REDIS_PORT=6380`. An explicit `DB_URL` overrides `POSTGRES_PORT`. Compose loads its `.env` automatically; Spring Boot does not, so a Compose-only `.env` port override must also be exported to the app process. Production-style Redis configuration can use `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT`.

Stop dependencies with `docker compose down`. To remove the local demo database volume and all its records:

```sh
docker compose down -v
```

Mesh reset is different: it clears in-memory packet holdings and this app's Redis idempotency namespace, but preserves balances, payments, ledger entries, devices, links, and route history.

Run with the production-style PostgreSQL profile:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

## Run Tests

Frontend verification is independent of the Maven backend suite. See
[frontend development instructions](frontend-tools/README.md), the
[audit](frontend-tools/AUDIT.md), [implementation report](frontend-tools/REPORT.md) and
[deferred suggestions](SUGGESTIONS.md). An isolated Java 17 preview uses port 18080 and
separate data/key storage. Frontend tooling is development-only: normal Maven startup
does not require npm installation or compilation. The independent frontend workflow checks
behavior, visuals, computed styles, axe and Lighthouse, and uploads reports.

```powershell
.\mvnw.cmd test
```

Linux/macOS: `./mvnw test`.

Tests use PostgreSQL and Redis Testcontainers. They do not use the local Compose database.

Latest verified suite (2026-10-03, Windows 11, Temurin Java 17.0.20.1, Maven wrapper 3.9.9):

```text
Tests run: 62
Failures: 0
Errors: 0
Skipped: 0
```

This includes PostgreSQL migration/constraint tests and real Redis/PostgreSQL duplicate-processing integration tests. The optional baseline run is separate: 1 measurement test passed with no failures, errors, or skips.

CI in `.github/workflows/ci.yml` runs `./mvnw test` on every push and pull request using Java 17, Maven caching, and Docker-backed PostgreSQL/Redis Testcontainers. Failure reports are uploaded from `target/surefire-reports`. A hosted GitHub Actions run has not yet been observed for these local changes.

Fresh-start verification on 2026-10-03 used a clean source export without `.env`, build output, or a wrapper JAR, plus a separate Compose project and new volume. `docker compose up -d --wait` made both services healthy; Unix wrapper bootstrap downloaded and verified the JAR; `mvnw.cmd spring-boot:run` applied all 14 migrations. The dashboard returned HTTP 200, five seeded accounts loaded, send/four gossip rounds/flush settled once, a second flush returned `DUPLICATE`, and reconciliation stayed balanced. Temporary ports were 55439 (PostgreSQL), 6389 (Redis), and 18089 (HTTP) to avoid existing services. Both Windows `mvnw.cmd test` and Unix `./mvnw test` under Git Bash passed the 62-test suite. Native Linux execution remains the CI runner's verification step.

## Duplicate Delivery Contract

- An in-flight duplicate direct upload can return HTTP 200 with `outcome: DUPLICATE`.
- A finalized direct upload replay returns HTTP 409 and `errorCode: DUPLICATE_PAYMENT` in the safe error envelope.
- Mesh flush returns per-upload outcomes, including `DUPLICATE`, inside its normal HTTP 200 batch response. It does not remove bridge-held copies; repeated flushes may upload them again.
- Redis blocks normal concurrent duplicate processing. Retryable failures can be retried; PostgreSQL settlement locking, payment/nonce uniqueness, transaction identity, and ledger constraints remain the final integrity protection, including after Redis eviction/reset.
- Existing tests verify three concurrent bridges settle once, replay after Redis eviction/re-encryption cannot settle again, and ledger failures roll back balances. The Phase 0 repeated-flush test verifies one transaction, exactly two ledger entries, one debit/credit, and balanced reconciliation across two flushes.

## Local Baseline Measurements

Run independently of your local Compose database:

```sh
./mvnw -Pbaseline test
```

Windows: `.\mvnw.cmd -Pbaseline test`. The profile runs only the measurement class; run ordinary `test` separately for correctness coverage. It starts a real HTTP server on a random port, fresh PostgreSQL 16/Redis 7 containers, and temporary demo signing keys. It does not touch local demo balances or introduce production measurement endpoints.

Raw samples and environment metadata: `target/baseline/results.json`; summary: `target/baseline/summary.md`. Baseline Surefire reports have their own directory. Any unexpected HTTP status or failed ledger invariant fails the run; there are no latency pass/fail thresholds.

Measured locally on **2026-10-03**, source based on commit `0eafa3a` plus these Phase 0 changes: Windows 11 x64; AMD64 Family 25 Model 80, 16 logical processors; 15.40 GiB host RAM; Temurin 17.0.20.1; Maven 3.9.9; Docker Desktop 4.91.0 / Engine 29.8.0; `postgres:16-alpine` and `redis:7-alpine`. JVM maximum heap approximately 3.85 GiB; inherited JVM flag `-Djava.net.preferIPv4Stack=true`.

Five warm-up flows, then twenty measured flows; four gossip rounds per flow. Each flow also measures a separate direct-ingestion payment and duplicate replays. Read endpoints are measured after 50 settled payments, 100 ledger entries, and 100 recorded route hops, with five warm-ups and twenty samples per endpoint. Calls are sequential, without dashboard polling or concurrent traffic.

| Operation | Samples | Median ms | p95 ms | Median JDBC calls |
|---|---:|---:|---:|---:|
| Demo send | 20 | 14.19 | 21.13 | 4 |
| One gossip round | 80 | 17.18 | 22.57 | 8 |
| Bridge flush through settlement | 20 | 44.40 | 52.95 | 23 |
| Duplicate flush | 20 | 10.60 | 11.90 | 2 |
| Direct ingestion through settlement | 20 | 35.38 | 45.28 | 17 |
| Duplicate direct ingestion | 20 | 8.18 | 11.05 | 0 |
| `/api/accounts` | 20 | 3.93 | 4.59 | 1 |
| `/api/payments` | 20 | 20.66 | 21.99 | 21 |
| `/api/transactions` | 20 | 19.29 | 21.93 | 21 |
| `/api/dashboard/summary` | 20 | 16.80 | 19.83 | 13 |
| `/api/mesh/state` | 20 | 7.55 | 8.50 | 2 |
| `/api/mesh/routes` | 20 | 29.58 | 31.86 | 31 |
| `/api/devices` | 20 | 13.20 | 17.16 | 11 |
| `/api/reconciliation` | 20 | 3.88 | 4.69 | 1 |

The representative send + four gossip rounds + flush uses **59 JDBC execution calls** (4 + 4 x 8 + 23). The test-only counter includes Hibernate and direct JDBC execution calls, including failed attempts; batches count as one call and statements executed internally by PostgreSQL triggers are excluded. Fixture setup and verification queries are outside measurement windows. This is not a query optimizer or server-wide statement count.

Memory after application readiness: heap **66.34 MiB**, non-heap **141.49 MiB**. After the first successful mesh payment flow: heap **73.38 MiB**, non-heap **154.77 MiB**. These are JVM used-memory snapshots including the test harness, not process RSS or container memory, and do not force garbage collection.

Settlement latency measures from HTTP upload request dispatch until the synchronous successful response, followed by independent committed-payment/ledger verification. It includes local HTTP, validation, signing verification, and database commit; it excludes time spent waiting offline. Duplicate checks left 50 unique payments, 50 transactions, and 100 ledger entries, with Alice debited exactly 50.00 and reconciliation balanced.

These are small local baseline measurements, not throughput claims or production SLAs. The read query counts are recorded as a starting point for later investigation; Phase 0 does not change fetch strategies or settlement behavior.

## Retained Legacy Identifiers

The Maven artifact is `com.demo:meshpay-backend`; the project name and dashboard branding are MeshPay. Java package/application identifiers (`com.demo.upimesh`, `UpiMeshApplication`), database name/credentials (`upi_mesh`, `upimesh_dev`), `upi.mesh.*` settings, `upi:mesh:idempotency:` Redis keys, and the `upi-mesh-demo-keys` directory remain for compatibility. Historical Flyway migration contents, including the legacy UUID seed prefix, remain unchanged to preserve checksums and identity.

## Main API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/` | Dashboard |
| `GET` | `/api/server-key` | Current public key |
| `GET` | `/api/accounts` | Account balances |
| `GET` | `/api/transactions` | Recent transactions |
| `GET` | `/api/payments` | Recent payment lifecycle records |
| `GET` | `/api/payments/{paymentId}` | One payment lifecycle record |
| `GET` | `/api/reconciliation` | Balance reconciliation report |
| `GET` | `/api/dashboard/summary` | Dashboard totals |
| `POST` | `/api/demo/send` | Create and inject encrypted demo packet |
| `GET` | `/api/mesh/state` | Mesh device state |
| `POST` | `/api/mesh/gossip` | Run one gossip round |
| `POST` | `/api/mesh/flush` | Upload bridge-held packets |
| `POST` | `/api/mesh/reset` | Clear mesh packets and app Redis keys |
| `POST` | `/api/bridge/ingest` | Ingest a bridge packet |
| `POST`, `GET` | `/api/devices` | Register or list devices |
| `GET` | `/api/devices/{deviceId}` | Read one device |
| `PATCH` | `/api/devices/{deviceId}/status` | Change device status or trust |
| `POST`, `GET` | `/api/mesh/connections` | Create/update or list directed links |
| `GET` | `/api/mesh/routes` | Recent routes; filter by `packetId` or `paymentId` |

## Demo Flow

1. Send demo payment with `/api/demo/send`.
2. Gossip packets through the mesh with `/api/mesh/gossip`.
3. Flush bridge devices with `/api/mesh/flush`.
4. Inspect payments, transactions, balances, and reconciliation.

Default virtual devices:

- `phone-alice`
- `phone-stranger1`
- `phone-stranger2`
- `phone-stranger3`
- `phone-bridge`

Only `phone-bridge` has internet access. The demo topology is a directed chain in
the order shown above, so a packet from Alice reaches the bridge after four gossip rounds.
Alice, Bob, and Carol each sign with their own registered device.

In dev/test, sender private keys are generated in
`${java.io.tmpdir}/upi-mesh-demo-keys` by default; set `upi.mesh.demo-key-dir` to
choose another local directory. The database holds only public keys. Losing a
private-key file while its public key remains registered makes demo signing fail
until the key is deliberately reprovisioned. Production does not generate demo keys.

`POST /api/devices` accepts `deviceId`, `userVpa`, `deviceName`,
`internetCapability`, a Base64 X.509 Ed25519 `publicKey`, and `algorithm` set to
`Ed25519`. New devices start `ACTIVE` and `UNTRUSTED`; the demo admin status API
can set `trustStatus` to `TRUSTED`. These management APIs have no authentication
and are intended only for a trusted demo environment.

## Example Demo Payment

```http
POST /api/demo/send
Content-Type: application/json

{
  "senderVpa": "alice@demo",
  "receiverVpa": "bob@demo",
  "amount": 50.00,
  "ttl": 5,
  "startDevice": "phone-alice"
}
```

The optional demo PIN is ignored for authorization and is never included in the
encrypted instruction. The sender device signs the canonical payment fields,
and ingestion records the verification outcome before any settlement.

## Error Format

```json
{
  "timestamp": "2026-09-26T12:00:00Z",
  "status": 400,
  "errorCode": "INVALID_PACKET",
  "message": "Request could not be processed.",
  "path": "/api/bridge/ingest",
  "traceId": "trace-id-value"
}
```

Clients may send `X-Trace-Id`. The server returns the trace ID in responses and logs.

## Project Layout

```text
src/main/java/com/demo/upimesh/
  controller/   REST and dashboard endpoints
  crypto/       key generation and hybrid encryption
  dto/          request and response records
  exception/    domain errors and global handler
  mapper/       response mapping
  model/        Account, Payment, Transaction, LedgerEntry, packet models
  repository/   Spring Data repositories
  service/      mesh, bridge, payment, settlement, reconciliation logic

src/main/resources/
  db/migration/       production Flyway migrations
  db/demo-migration/  dev/test seed data
  static/             dashboard assets
  templates/          Thymeleaf dashboard
```

## Key Limitations

- Packet holdings are simulated in memory; device registration, links, and route history are persistent.
- No real Bluetooth, Android, or iOS client is included.
- No real NPCI, bank, KYC, fraud, or PIN verification integration exists.
- RSA keys are generated at startup and are not production key management.
- Bridge authentication is not implemented.
- Compose credentials are for local development only.

## License

This project is licensed under the [MIT License](LICENSE).

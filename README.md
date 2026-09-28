# UPI Offline Mesh

Spring Boot backend for a simulated offline UPI-style payment flow. A sender creates an encrypted payment packet, nearby virtual devices gossip it through an offline mesh, and an internet-connected bridge uploads it for deferred settlement.

This is a backend engineering demo. It is not a real UPI, NPCI, or bank integration.

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

`LedgerEntry` records actual financial movement:

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

PostgreSQL is the real database for dev, test, and production.

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

Start PostgreSQL and Redis:

```powershell
docker compose up -d --wait
```

Run the app:

```powershell
.\mvnw.cmd spring-boot:run
```

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

Run with the production-style PostgreSQL profile:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

## Run Tests

```powershell
.\mvnw.cmd test
```

Tests use PostgreSQL and Redis Testcontainers. They do not use the local Compose database.

Latest verified suite:

```text
Tests run: 58
Failures: 0
Errors: 0
```

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

No license file is included. Treat this as demonstration code unless a license is added.

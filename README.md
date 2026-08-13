# Ledger-Core

A backend money-movement service built with **Spring Boot** and **PostgreSQL**, designed around the kind of correctness guarantees real banking systems depend on — atomic transactions, double-entry bookkeeping, safe handling of concurrent requests, idempotent transfers, and automated balance reconciliation.

This is a learning project I built to go deeper than CRUD: the goal was a small but genuinely **correct-under-failure** system, the way money systems actually have to behave.

*Note: at this project's scale, Kafka isn't a need — it's here deliberately, to demonstrate event-driven design and idempotent consumption alongside the transaction-safety and concurrency work elsewhere in the project.*

🔗 **Live demo:** `https://ledger-core.onrender.com`
(Hosted on Render with a managed Neon PostgreSQL database. First request after idle may take ~30–60s to wake up.)

---

## What it does

A REST API for opening accounts and moving money between them — deposits, withdrawals, and transfers — where every operation is safe against the things that break naive implementations: crashes mid-transaction, two requests racing on the same account, and duplicate requests from network retries.

---

## Key features

**Accurate money handling**
All amounts use `BigDecimal` and a `NUMERIC(19,2)` column type — never floating point, which can't represent money exactly. Input is validated at the API boundary (positive amounts, bounded size).

**Atomic transactions**
Every money movement runs inside a single database transaction. If anything fails partway through, the whole operation rolls back — money never half-moves or disappears.

**Concurrency safety (optimistic locking)**
Accounts carry a version field, so two transfers hitting the same account at the same instant can't both succeed on stale data. Proven with a concurrency test that fires 10 simultaneous withdrawals at an account that can only cover some of them, and confirms it never overdraws.

**Double-entry bookkeeping**
Every movement writes two ledger entries — a debit and a credit that net to zero — through a system settlement account, just like real accounting. Balances can always be recomputed from the immutable ledger, which is the true source of record.

**Append-only ledger**
The ledger table is protected at the database level by a trigger that rejects any update or delete — history can be added to but never altered or erased, even outside the application.

**Idempotent transfers**
Transfers require an `Idempotency-Key` header. If a client retries the same request (e.g. after a timeout), the money moves only once and the original result is returned. Reusing a key for a genuinely different request is safely rejected.

**Authentication**
Every endpoint except health and auth itself requires a signed-in user. Register or log in with email + password (hashed with bcrypt, no email verification) to get back a stateless JWT; requests carry it as `Authorization: Bearer <token>`. A lightweight USER/ADMIN role gates the reconciliation admin endpoints to ADMIN only.

**Automated reconciliation**
A scheduled job re-checks every account's stored balance against its ledger-derived balance and records any mismatch as a tracked "break" — the same end-of-day integrity check banks run. It detects discrepancies and never silently "fixes" them, preserving the audit trail.

**Consistent error handling**
All errors return a single, predictable JSON shape with the right HTTP status (400 / 404 / 409 / 422 / 500), and internal details are never leaked to the client.

---

## Tech stack

- **Java 21**, **Spring Boot**
- **PostgreSQL** (managed via Neon in production)
- **Spring Data JPA / Hibernate**
- **Spring Security** + **JWT** (jjwt) for authentication
- **Flyway** for versioned database migrations
- **Apache Kafka** (spring-kafka) for `TransferCompletedEvent` publishing — see [Design decisions](#design-decisions-worth-noting)
- **JUnit 5 + AssertJ + Mockito** for testing
- **Docker** for containerized deployment
- Deployed on **Render**

---

## Project structure

Package-by-feature, and layered the same way inside each feature:

```
src/main/java/com/anish/banking/bank/
├── auth/              # register, login, JWT issue/verify, roles
│   ├── controller/  dto/  exception/  model/  repository/  security/  service/
├── ledger/
│   ├── account/       # same controller/dto/exception/model/repository/service shape
│   ├── transfer/
│   ├── ledger/        # ledger entries — model/repository only, nothing external calls it directly
│   └── idempotency/
├── reconciliation/
└── common/            # cross-cutting: the one ApiError shape, CORS, health check
```

Every feature gets `controller/ dto/ model/ repository/ service/` (plus `exception/` where it has its own exceptions) — but only the folders it actually needs: `ledger.ledger` has no controller, and `idempotency`'s `RequestHasher` is a plain static helper, not forced into `service/` just to fit the pattern.

---

## API overview

Base path: `/api`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `GET`  | `/api/health` | Health check | none |
| `POST` | `/api/auth/register` | Register (email + password) → returns a JWT | none |
| `POST` | `/api/auth/login` | Log in → returns a JWT | none |
| `POST` | `/api/accounts` | Open an account | required |
| `GET`  | `/api/accounts/{id}/balance` | Get balance | required |
| `POST` | `/api/accounts/{id}/deposit` | Deposit funds | required |
| `POST` | `/api/accounts/{id}/withdraw` | Withdraw funds | required |
| `POST` | `/api/transfers` | Transfer between accounts (requires `Idempotency-Key` header) | required |
| `GET`  | `/api/transfers/{id}` | Get a transfer | required |
| `POST` | `/api/admin/reconciliation/run` | Trigger a reconciliation sweep | ADMIN role |
| `GET`  | `/api/admin/reconciliation/breaks` | View detected discrepancies | ADMIN role |

"Required" means a valid `Authorization: Bearer <token>` header, obtained from `/api/auth/register` or `/api/auth/login`. A missing or invalid token gets a `401`; a valid token without the `ADMIN` role on an admin endpoint gets a `403` — both in the same `ApiError` shape as every other error.

**Example — register, then a transfer:**
```bash
TOKEN=$(curl -s -X POST https://ledger-core.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"a-strong-password"}' | jq -r .token)

curl -X POST https://ledger-core.onrender.com/api/transfers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":50.00}'
```

---

## How it was built

The project was built in phases, each adding one correctness guarantee on top of a working base, and each backed by a test that proves the property holds:

| Concept | What it demonstrates |
|---------|----------------------|
| Atomic transactions | How transactions and rollbacks protect money movement |
| Optimistic locking | Handling concurrent updates without corruption |
| Double-entry ledger | Detecting data corruption via balanced, immutable records |
| Idempotency | Preventing double-charges on retried requests |
| Reconciliation | Automatically verifying the books balance |
| Authentication | Stateless JWT login gate + role-based authorization, verified end-to-end (register → token → protected route → role-gated route) |

---

## Running it locally

```bash
# Start PostgreSQL
docker compose up -d

# Run the app (Flyway builds the schema automatically on startup)
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

---

## Design decisions worth noting

- **Stored balance + ledger as source of truth.** Each account keeps a fast-read balance column, but the ledger is authoritative — the balance is updated transactionally alongside ledger entries, and reconciliation independently verifies they agree. This is what makes the reconciliation feature meaningful.
- **Optimistic over pessimistic locking.** Most accounts are rarely contended, so letting reads proceed freely and only failing on a real conflict is cheaper than locking every access. The trade-off is documented and tested.
- **Validated at the boundary, enforced in the core.** Business rules like "balance can't go negative" live inside the entity itself, so they can't be bypassed by any code path.
- **Event publishing happens after commit, not inside the transaction.** A successful transfer publishes a `TransferCompletedEvent` to Kafka (topic `transfer.completed`, keyed by transfer ID) via `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` — never a direct `kafkaTemplate.send()` inside `TransferService`. Publishing before commit would let a later rollback announce money movement that never actually happened; publishing after guarantees the event only exists if the transfer does. A Kafka failure at that point is logged and swallowed, never rethrown — the transfer already committed, so a messaging outage has no business turning into a false error for a caller whose money already moved. The companion `ledger-notifier` service (a separate Spring Boot app, `../ledger-notifier`) consumes that topic and stays idempotent under redelivery or concurrent delivery the same way the ledger itself stays correct under concurrency: not a check-then-insert (which races), but a single atomic `INSERT ... ON CONFLICT (transfer_id) DO NOTHING`, so a duplicate delivery is a guaranteed no-op rather than a hope.

---

## Roadmap

Authentication and event publishing have landed: email + password login, JWT-protected endpoints, an ADMIN role for the reconciliation endpoints, and a Kafka `TransferCompletedEvent` consumed idempotently by a separate `ledger-notifier` service. Deliberately still open:

- **Account ownership.** Auth today is a login gate, not per-resource authorization — any authenticated user can act on any account. Tying accounts to their owning user, and scoping the account/transfer endpoints to it, is the natural next step.

---

*Built as a portfolio project to demonstrate backend fundamentals for financial systems: transaction safety, data integrity, concurrency, and clean API design.*
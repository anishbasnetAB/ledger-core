# Ledger-Core

A backend money-movement service built with **Spring Boot** and **PostgreSQL**, designed around the kind of correctness guarantees real banking systems depend on — atomic transactions, double-entry bookkeeping, safe handling of concurrent requests, idempotent transfers, and automated balance reconciliation.

This is a learning project I built to go deeper than CRUD: the goal was a small but genuinely **correct-under-failure** system, the way money systems actually have to behave.

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

**Automated reconciliation**
A scheduled job re-checks every account's stored balance against its ledger-derived balance and records any mismatch as a tracked "break" — the same end-of-day integrity check banks run. It detects discrepancies and never silently "fixes" them, preserving the audit trail.

**Consistent error handling**
All errors return a single, predictable JSON shape with the right HTTP status (400 / 404 / 409 / 422 / 500), and internal details are never leaked to the client.

---

## Tech stack

- **Java 21**, **Spring Boot**
- **PostgreSQL** (managed via Neon in production)
- **Spring Data JPA / Hibernate**
- **Flyway** for versioned database migrations
- **JUnit 5 + AssertJ + Mockito** for testing
- **Docker** for containerized deployment
- Deployed on **Render**

---

## API overview

Base path: `/api`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/health` | Health check |
| `POST` | `/api/accounts` | Open an account |
| `GET`  | `/api/accounts/{id}/balance` | Get balance |
| `POST` | `/api/accounts/{id}/deposit` | Deposit funds |
| `POST` | `/api/accounts/{id}/withdraw` | Withdraw funds |
| `POST` | `/api/transfers` | Transfer between accounts (requires `Idempotency-Key` header) |
| `GET`  | `/api/transfers/{id}` | Get a transfer |
| `POST` | `/api/admin/reconciliation/run` | Trigger a reconciliation sweep |
| `GET`  | `/api/admin/reconciliation/breaks` | View detected discrepancies |

**Example — a transfer:**
```bash
curl -X POST https://ledger-core.onrender.com/api/transfers \
  -H "Content-Type: application/json" \
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

---

## Roadmap

The next planned phase is **authentication & authorization** — login, and ensuring a user can only act on accounts they own. The current deployment is a demonstration and its endpoints are intentionally open; user identity and access control are the documented next step.

---

*Built as a portfolio project to demonstrate backend fundamentals for financial systems: transaction safety, data integrity, concurrency, and clean API design.*
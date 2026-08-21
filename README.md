# Ledger-Core

A backend money-movement service built with **Spring Boot** and **PostgreSQL**, designed around the kind of correctness guarantees real banking systems depend on — atomic transactions, double-entry bookkeeping, safe handling of concurrent requests, idempotent transfers, and automated balance reconciliation.

This is a learning project I built to go deeper than CRUD: the goal was a small but genuinely **correct-under-failure** system, the way money systems actually have to behave.

*Note: at this project's scale, Kafka isn't a need — it's here deliberately, to demonstrate event-driven design and idempotent consumption alongside the transaction-safety and concurrency work elsewhere in the project.*

🔗 **Live demo:** `https://ledger-core-frontend-anish.vercel.app/` **Hosted on AWS**

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

Validated with a 1,000-transfer concurrent stress test over real HTTP against real Postgres (16 workers, 50 of the transfers additionally raced 3-way on the same `Idempotency-Key`): zero duplicate postings, every transfer balanced and correctly reflected in both accounts, zero genuine failures. Reproducible in one command — see the [test](src/test/java/com/anish/banking/bank/stress/TransferVolumeStressTest.java) and its generated [report](docs/transfer-stress-test-report.md) (this is a correctness/stress result on one local machine, not a production scalability benchmark — the report's "Limits" section spells that out).

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
- **Redis** (spring-data-redis / Lettuce) for balance-read caching and rate limiting — see [Design decisions](#design-decisions-worth-noting)
- **JUnit 5 + AssertJ + Mockito** for testing
- **Docker** for containerized deployment
- **Kubernetes** (k3s, single-node) for a process-level HA demo — see [`k8s/`](k8s/README.md) and [Design decisions](#design-decisions-worth-noting)
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

`POST /api/auth/login` and `POST /api/transfers` are rate-limited; going over the limit gets a `429` in the same `ApiError` shape — wait for the current window to pass and retry (see [Design decisions](#design-decisions-worth-noting)).

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
# Start Postgres, Kafka, and Redis
docker compose up -d

# Run the app (Flyway builds the schema automatically on startup)
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Redis and Kafka are both best-effort — the app
still starts and serves requests without them, just without caching/eviction or event
publishing actually reaching anywhere (see [Design decisions](#design-decisions-worth-noting)).

---

## Design decisions worth noting

- **Stored balance + ledger as source of truth.** Each account keeps a fast-read balance column, but the ledger is authoritative — the balance is updated transactionally alongside ledger entries, and reconciliation independently verifies they agree. This is what makes the reconciliation feature meaningful.
- **Optimistic over pessimistic locking.** Most accounts are rarely contended, so letting reads proceed freely and only failing on a real conflict is cheaper than locking every access. The trade-off is documented and tested.
- **Validated at the boundary, enforced in the core.** Business rules like "balance can't go negative" live inside the entity itself, so they can't be bypassed by any code path.
- **Event publishing happens after commit, not inside the transaction.** A successful transfer publishes a `TransferCompletedEvent` to Kafka (topic `transfer.completed`, keyed by transfer ID) via `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` — never a direct `kafkaTemplate.send()` inside `TransferService`. Publishing before commit would let a later rollback announce money movement that never actually happened; publishing after guarantees the event only exists if the transfer does. A Kafka failure at that point is logged and swallowed, never rethrown — the transfer already committed, so a messaging outage has no business turning into a false error for a caller whose money already moved. The companion `ledger-notifier` service (a separate Spring Boot app, `../ledger-notifier`) consumes that topic and stays idempotent under redelivery or concurrent delivery the same way the ledger itself stays correct under concurrency: not a check-then-insert (which races), but a single atomic `INSERT ... ON CONFLICT (transfer_id) DO NOTHING`, so a duplicate delivery is a guaranteed no-op rather than a hope.
- **Balance cache is a safety net, not a source of truth — and it knows it.** `GET /api/accounts/{id}/balance` caches its response in Redis (`balance:{accountId}`, 45s TTL) and `BalanceService` evicts that key on every deposit, withdrawal, and transfer touching the account — reusing the exact same `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` pattern as the Kafka publish, so an eviction can't fire for a change that then rolls back. A Redis failure on either side (read *or* write *or* evict) is caught and logged, never surfaced to the caller: the worst case is a read that's up to 45 seconds stale, and the value it went stale from — the stored balance column — is independently re-verified against the ledger by the scheduled reconciliation job regardless of what's cached. A stale cache is a UX nit here, not a correctness bug, which is exactly why it's allowed to exist at all next to a system this paranoid about correctness everywhere else.
- **Rate limiting is fixed-window, on purpose, with its failure mode chosen deliberately.** `POST /api/auth/login` and `POST /api/transfers` are limited via a Redis `INCR` per `{identity}:{endpoint}:{windowStart}` key, capped and reset once per window (defaults: 5/min for login, 20/min for transfers — login is stricter since it's the more attractive brute-force target). Fixed-window is the simplest correct implementation, and its known weakness is accepted rather than hidden: a client can burst up to ~2x the limit across a window boundary (e.g. 5 requests in the last second of one window, 5 more in the first second of the next). A sliding-window or token-bucket limiter would close that gap at the cost of real complexity for a portfolio project defending against casual abuse, not a targeted attacker — not a trade worth making here. The other deliberate choice is the failure mode: if Redis itself is unreachable, the filter fails *open* (logs the error, lets the request through) rather than closed. A rate limiter's job is defense in depth; a rate limiter whose own infrastructure outage blocks every login and transfer would be a self-inflicted denial of service, which is a worse failure than no rate limiting at all.
- **The Kubernetes setup demonstrates process-level HA, not node-level HA — and says so.** [`k8s/`](k8s/README.md) runs 3 replicas of this app on a single-node k3s cluster (one EC2 instance). That's enough to genuinely demonstrate pod crash recovery and zero-downtime rolling deploys — a killed pod gets rescheduled automatically, and a rollout (`maxUnavailable: 0`, gated on the `/api/health` readinessProbe) never drops the Service below 3 working backends. What it does *not* demonstrate is surviving a machine failure: all 3 replicas share one instance, so if that instance goes down, all 3 go down together. True infra-level HA needs multiple nodes — e.g. EKS spread across availability zones — and that's deliberately out of scope here; it's a different exercise (real cloud infra cost and complexity) from proving the Kubernetes mechanics work. In the same spirit as everywhere else in this project, no Helm chart, no kustomize overlays, no HPA/autoscaling, no service mesh, no Ingress controller, no multi-node config — a toy app demonstrating pod-level HA doesn't need any of them, and the smallest deployment that proves the concept beats undemonstrated complexity.

---

## Roadmap

Authentication, event publishing, Redis, and a Kubernetes demo have landed: email + password login, JWT-protected endpoints, an ADMIN role for the reconciliation endpoints, a Kafka `TransferCompletedEvent` consumed idempotently by a separate `ledger-notifier` service, a balance read cache, fixed-window rate limiting on login/transfers, and a 3-replica k3s deployment demonstrating process-level HA. Deliberately still open:

- **Account ownership.** Auth today is a login gate, not per-resource authorization — any authenticated user can act on any account. Tying accounts to their owning user, and scoping the account/transfer endpoints to it, is the natural next step.
- **True (node-level) HA.** The current k3s setup is single-node — 3 replicas sharing one EC2 instance, which demonstrates the Kubernetes mechanics but not survival of a machine failure. Multi-node (e.g. EKS spread across availability zones) is the natural next step for that, and deliberately out of scope for now — real cloud infra and cost, not just another YAML file.

---

*Built as a portfolio project to demonstrate backend fundamentals for financial systems: transaction safety, data integrity, concurrency, and clean API design.*

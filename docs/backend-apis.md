# Backend API Reference

For frontend developers building against this API. Every endpoint, what to send, what comes back, and what to build a screen around.

## Base URL & content type

| Environment | Base URL |
|---|---|
| Local | `http://localhost:8080` |
| Production | `https://ledger-core.onrender.com` |

All request/response bodies are `application/json`. Send `Content-Type: application/json` on every request with a body.

## CORS

The API allows cross-origin requests from `http://localhost:*`, `http://127.0.0.1:*`, and `https://ledger-core-frontend*.vercel.app` (configurable server-side via `CORS_ALLOWED_ORIGINS`). Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.

`allowCredentials` is **off** — this API doesn't use cookies. Don't send `credentials: 'include'`; auth is entirely via the `Authorization` header (see below).

---

## Authentication

Every endpoint requires a signed-in user **except** `GET /api/health`, `POST /api/auth/register`, and `POST /api/auth/login`.

1. Call `register` or `login` to get a JWT.
2. Store it client-side (e.g. in memory or `localStorage`).
3. Send it on every other request:
   ```
   Authorization: Bearer <token>
   ```

Tokens are stateless and expire **24 hours** after issue (`app.jwt.expiration-minutes`, default 1440). There is no refresh endpoint — when a token expires, the user logs in again. There is no email verification step; an account is usable immediately after registering.

Two roles exist: `USER` (default for everyone who registers) and `ADMIN` (gates the reconciliation endpoints; there's no self-service way to become admin — it's promoted directly in the database).

### `POST /api/auth/register`

Create an account and get a token immediately.

**Auth:** none

**Request body:**
```json
{
  "email": "you@example.com",
  "password": "at-least-8-characters"
}
```
| Field | Type | Rules |
|---|---|---|
| `email` | string | required, must be a valid email address |
| `password` | string | required, min 8 characters |

**Response `201 Created`:**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "email": "you@example.com",
  "role": "USER"
}
```

**Errors:** `400` invalid email/password (see [Error format](#error-format)), `409` email already registered.

### `POST /api/auth/login`

**Auth:** none

**Request body:**
```json
{ "email": "you@example.com", "password": "at-least-8-characters" }
```

**Response `200 OK`:** same shape as register — `{ token, email, role }`.

**Errors:** `400` blank email/password, `401` wrong email or password (deliberately the same message for both — don't reveal which one was wrong).

---

## Error format

Every error response, from every endpoint, has this exact shape:

```json
{
  "timestamp": "2026-08-13T10:09:51.31Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found: 42",
  "path": "/api/accounts/42/balance",
  "fieldErrors": null
}
```

`fieldErrors` is only present (a non-null array of `{ "field": "...", "message": "..." }`) on request-validation failures (`400`s from a bad request body). Build one error handler in the frontend around this shape rather than one per endpoint.

| Status | Meaning | Typical cause |
|---|---|---|
| `400` | Bad request | Failed validation, malformed JSON, missing required header, transfer source == destination |
| `401` | Unauthenticated | Missing/expired/invalid token, or wrong login credentials |
| `403` | Forbidden | Valid token, but the endpoint needs `ADMIN` and the user is `USER` |
| `404` | Not found | Account or transfer id doesn't exist |
| `409` | Conflict | Email already registered, or an `Idempotency-Key` reused with a different request body |
| `422` | Unprocessable | Insufficient funds, or a transfer between accounts with different currencies |
| `500` | Server error | Unexpected — the message is always generic, nothing internal is leaked |

---

## Accounts

### `POST /api/accounts`

Open an account.

**Auth:** required

**Request body:**
```json
{ "ownerName": "Alice", "currency": "CAD" }
```
| Field | Type | Rules |
|---|---|---|
| `ownerName` | string | required, max 255 chars |
| `currency` | string | required, 3-letter ISO code (e.g. `CAD`, `USD`) — case-insensitive, stored upper-cased |

**Response `201 Created`** (with a `Location: /api/accounts/{id}` header):
```json
{ "id": 7, "ownerName": "Alice", "currency": "CAD", "balance": 0.00 }
```

**Errors:** `400` validation.

### `GET /api/accounts/{id}/balance`

**Auth:** required

**Response `200 OK`:**
```json
{ "accountId": 7, "currency": "CAD", "balance": 900.00 }
```

**Errors:** `404` no such account.

### `POST /api/accounts/{id}/deposit`

**Auth:** required

**Request body:**
```json
{ "amount": 100.00 }
```
| Field | Type | Rules |
|---|---|---|
| `amount` | number | required, ≥ 0.01, at most 2 decimal places |

**Response `200 OK`:** a `BalanceResponse`, same shape as `GET .../balance`, reflecting the new balance.

**Errors:** `404` no such account, `400` validation.

### `POST /api/accounts/{id}/withdraw`

Same request/response shape as deposit.

**Errors:** `404` no such account, `400` validation, `422` insufficient funds.

---

## Transfers

### `POST /api/transfers`

Move money between two accounts of the same currency.

**Auth:** required, **plus** an `Idempotency-Key` header (any client-generated unique string, e.g. `crypto.randomUUID()`). Retrying the exact same request with the same key returns the original result instead of moving money twice — generate one key per user-initiated transfer attempt, and reuse it only when retrying that same attempt (e.g. after a network timeout).

**Headers:**
```
Authorization: Bearer <token>
Idempotency-Key: <uuid>
```

**Request body:**
```json
{ "sourceAccountId": 1, "destinationAccountId": 2, "amount": 50.00 }
```
| Field | Type | Rules |
|---|---|---|
| `sourceAccountId` | number | required |
| `destinationAccountId` | number | required, must differ from `sourceAccountId` |
| `amount` | number | required, positive |

**Response `201 Created`** (with a `Location: /api/transfers/{id}` header):
```json
{
  "transferId": 55,
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 50.00,
  "status": "COMPLETED"
}
```

**Errors:** `400` missing `Idempotency-Key` header, validation, or same source/destination; `404` either account doesn't exist; `409` the `Idempotency-Key` was already used for a *different* request body; `422` insufficient funds or currency mismatch.

### `GET /api/transfers/{id}`

**Auth:** required

**Response `200 OK`:** a `TransferResponse`, same shape as above.

**Errors:** `404` no such transfer.

---

## Reconciliation (admin only)

Internal/ops endpoints — a `USER` token gets `403` on both. Not something a typical end-user-facing frontend needs; build these only if you're building an admin dashboard.

### `POST /api/admin/reconciliation/run`

Runs a reconciliation sweep on demand (it also runs automatically on a schedule).

**Auth:** required, role `ADMIN`

**Response:** `200 OK`, empty body.

### `GET /api/admin/reconciliation/breaks`

Lists every detected stored-vs-ledger balance mismatch, past and present.

**Auth:** required, role `ADMIN`

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "accountId": 42,
    "ledgerDerivedBalance": 200.00,
    "storedBalance": 999.99,
    "status": "OPEN",
    "detectedAt": "2026-08-13T03:00:00Z",
    "lastDetectedAt": "2026-08-13T03:00:00Z",
    "resolvedAt": null
  }
]
```
`status` is `OPEN` (still mismatched) or `RESOLVED` (closed automatically once a later sweep finds it back in sync — nothing is ever silently corrected).

---

## Health

### `GET /api/health`

**Auth:** none. Use this for an uptime check or to warm up the Render instance before showing a login screen (first request after idle can take 30–60s).

**Response `200 OK`:**
```json
{ "status": "UP", "checkedAt": "2026-08-13T10:09:51.31Z" }
```

---

## What to build

A minimal frontend needs:

1. **Register / login screen** → `POST /api/auth/register` or `/login`, store the returned token, attach it to everything after.
2. **Account list / detail** → `POST /api/accounts` to open one, `GET /api/accounts/{id}/balance` to show it.
3. **Deposit / withdraw forms** → `POST /api/accounts/{id}/deposit` / `.../withdraw`, re-render the balance from the response.
4. **Transfer form** → generate an `Idempotency-Key` per submit attempt, `POST /api/transfers`, show the result; `GET /api/transfers/{id}` if you need a transfer detail/history view.
5. **One shared error handler** → keyed off the `status` field in the [error format](#error-format) above: `400` → show field errors inline, `401` → send back to login (token missing/expired), `403` → hide/disable admin-only UI, `404` → "not found" state, `409`/`422` → surface `message` as-is, it's already user-safe.
6. **(Optional) Admin reconciliation view** → only render this for a `role: "ADMIN"` user; the two endpoints above are all it needs.

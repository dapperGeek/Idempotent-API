# Idempotency in Spring Boot — Case Study

A production-grade implementation of idempotency patterns using Spring Boot, JPA, and Redis.

---

## Overview

This project demonstrates three levels of idempotency:

| HTTP Method | Endpoint            | Idempotency Type          |
|-------------|---------------------|---------------------------|
| `POST`      | `/api/orders`       | Enforced via Idempotency-Key header |
| `DELETE`    | `/api/orders/{id}`  | Natural (cancel is a no-op if already cancelled) |
| `GET`       | `/api/orders/{id}`  | Natural (reads never mutate state) |

---

## How It Works

### The Idempotency-Key Pattern (for POST)

```
Client                              Server
  │                                   │
  │──POST /api/orders ──────────────►│
  │  Idempotency-Key: uuid-abc        │  ← First time: execute operation
  │                                   │    Save result under key uuid-abc
  │◄──── 201 Created ────────────────│
  │      { id: 42, ... }             │
  │                                   │
  │  [Network timeout — client retries]
  │                                   │
  │──POST /api/orders ──────────────►│
  │  Idempotency-Key: uuid-abc        │  ← Same key: return cached result
  │                                   │    NO new order created
  │◄──── 200 OK ─────────────────────│
  │      { id: 42, idempotentReplay: true }
```

### Flow Inside the Application

```
OrderController.createOrder()
        │
        ▼
IdempotencyService.executeIdempotently()
        │
        ├─► Key found in DB? ──YES──► Return cached OrderResponse (no DB write)
        │
        └─► Key NOT found?
                │
                ▼
          OrderService.createOrder()   ← Side effect happens HERE (once!)
                │
                ▼
          Persist IdempotencyRecord     ← Cache the result
                │
                ▼
          Return OrderResponse
```

---

## Project Structure

```
src/main/java/com/example/idempotency/
├── controller/
│   └── OrderController.java          # REST endpoints with inline comments
├── service/
│   ├── IdempotencyService.java       # Core idempotency logic
│   ├── IdempotencyResult.java        # Result wrapper with fromCache flag
│   └── OrderService.java             # Business logic (create, cancel orders)
├── model/
│   ├── Order.java                    # Order JPA entity
│   ├── IdempotencyRecord.java        # Stored idempotency key + cached response
│   └── OrderDTOs.java               # Request/response DTOs
├── repository/
│   ├── OrderRepository.java
│   └── IdempotencyRecordRepository.java
├── exception/
│   ├── IdempotencyConflictException.java
│   └── GlobalExceptionHandler.java
└── config/
    └── AppConfig.java                # ObjectMapper, scheduling

src/test/java/com/example/idempotency/
└── IdempotencyIntegrationTest.java   # Full end-to-end tests
```

---

## Running the Application

### Prerequisites
- Java 17+
- Maven 3.8+
- Redis (optional — only needed for production distributed locking)

### Start

```bash
# Build
mvn clean package -DskipTests

# Run (uses H2 in-memory DB; disable Redis auto-config if not installed)
mvn spring-boot:run
```

---

## Testing the API

### 1. Create an order (first time — 201 Created)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"customerId":"C001","productId":"P001","quantity":2,"unitPrice":49.99}'

# Response:
# HTTP 201 Created
# { "id": 1, "idempotentReplay": false, ... }
```

### 2. Retry the same request (duplicate — 200 OK)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"customerId":"C001","productId":"P001","quantity":2,"unitPrice":49.99}'

# Response:
# HTTP 200 OK         ← Note: 200, not 201
# { "id": 1, "idempotentReplay": true, ... }   ← Same order ID!
```

### 3. Try without the header (400 Bad Request)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C001","productId":"P001","quantity":2,"unitPrice":49.99}'

# Response: HTTP 400 — Missing Required Header
```

### 4. Cancel an order (idempotent — call it 3 times, same result)

```bash
curl -X DELETE http://localhost:8080/api/orders/1
curl -X DELETE http://localhost:8080/api/orders/1  # Safe no-op
curl -X DELETE http://localhost:8080/api/orders/1  # Still safe
```

---

## Key Design Decisions

### Why DB-backed (not just Redis)?

This implementation persists idempotency records to the database for durability. If the server restarts after processing a request but before returning the response, the cached result survives. Redis can be added as a faster first-level cache layer on top.

### TTL on Idempotency Records

Records expire after 24 hours (configurable via `idempotency.ttl-minutes`). This matches the recommended Stripe convention. Clients should not reuse keys after expiry.

### 409 vs 422 for key conflicts

When the same key is used for a different endpoint, the app returns `422 Unprocessable Entity` — this signals the client has made a logical error (key misuse), not a protocol error.

### idempotentReplay flag in response

The response includes `"idempotentReplay": true` on duplicate requests. This is informational — clients can log it for debugging but should treat the response identically to the original.

---

## Natural Idempotency vs. Enforced Idempotency

| Concept | Description | Example |
|---------|-------------|---------|
| **Natural idempotency** | The operation is safe to repeat by definition | `DELETE`, `GET`, `PUT`, cancel an order |
| **Enforced idempotency** | The operation has side effects but is made safe via keys | `POST /orders`, `POST /payments` |
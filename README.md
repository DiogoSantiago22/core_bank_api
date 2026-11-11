# Core Bank API

A REST API for basic banking operations (accounts, deposits, withdrawals, transfers and transaction history), built with **Java 21, Spring Boot 3 and PostgreSQL**.

I built it to learn the Spring ecosystem outside of coursework: layered architecture, JPA, stateless JWT authentication and, mainly, how to keep money operations correct under concurrent access.

## Features

- Account sign-up and login (passwords hashed with **BCrypt**, **JWT** access tokens, stateless sessions).
- Deposit, withdraw and **transfer** between accounts, each recorded as a transaction.
- Transaction history per account, newest first.
- Ownership checks: a client can only withdraw from, transfer from and read the history of their own account.
- Consistent error responses (`400`, `401`, `403`, `404`) through a global exception handler.
- Request validation with Bean Validation (`@Valid`, `@NotBlank`, `@Positive`).

## Stack

| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 3, Spring Web, Spring Data JPA (Hibernate) |
| Security | Spring Security, JWT (`com.auth0:java-jwt`), BCrypt |
| Database | PostgreSQL |
| Tests | JUnit 5, Mockito |
| Packaging | Maven (wrapper included), Docker, Docker Compose |

## Architecture

```
controller  ->  service  ->  repository  ->  PostgreSQL
   |              |
  dto        security (JwtService, JwtAuthenticationFilter)
   |
exception (GlobalExceptionHandler)
```

- `controller`: HTTP layer, converts entities to response DTOs.
- `service`: business rules, transactions and ownership checks.
- `repository`: Spring Data JPA repositories.
- `dto`: Java `record`s for requests and responses, with validation annotations.
- `security`: JWT creation/validation and the filter that authenticates each request.

## Design decisions

**Money is `BigDecimal`**, never `double`, so there are no floating-point rounding errors.

**Concurrency.** Two simultaneous withdrawals on the same account could both read the same balance and overdraw it. Balance-changing operations therefore load the account with a **pessimistic write lock** (`SELECT ... FOR UPDATE`, via `findByIdForUpdate`) inside a `@Transactional` method.

**Deadlock prevention in transfers.** A transfer locks two accounts. If A->B and B->A run at the same time and each locks its own source first, they deadlock. Transfers instead acquire the locks **in a fixed order (by account id)**, regardless of direction.

**Atomic transfers.** The debit, the credit and both transaction records happen in a single database transaction, so a failure rolls everything back.

**Stateless auth.** No server-side session: the client sends `Authorization: Bearer <token>` and the filter validates the signature and expiry on every request.

## API

Public: `POST /api/accounts` (sign-up) and `POST /api/auth/login`. Everything else requires a `Bearer` token.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/accounts` | Create an account (`id`, `ownerName`, `email`, `password`, `role`) |
| `POST` | `/api/auth/login` | Log in with `email` and `password`, returns `{ "token": "..." }` |
| `GET` | `/api/accounts/{id}` | Get an account |
| `PUT` | `/api/accounts/{id}/deposit` | Deposit (`{ "amount": 50.00 }`) |
| `PUT` | `/api/accounts/{id}/withdraw` | Withdraw (`{ "amount": 20.00 }`) |
| `POST` | `/api/accounts/transfer` | Transfer (`{ "fromId", "toId", "amount" }`) |
| `GET` | `/api/accounts/{id}/transactions` | Transaction history |

Example:

```bash
# create an account
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"id":"100","ownerName":"Ana Silva","email":"ana@mail.com","password":"changeme","role":"CLIENT"}'

# log in and use the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@mail.com","password":"changeme"}' | jq -r .token)

curl -X PUT http://localhost:8080/api/accounts/100/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount": 50.00}'
```

Errors share one format:

```json
{ "timestamp": "...", "status": 403, "error": "Forbidden", "message": "Not your account", "path": "/api/accounts/100/withdraw" }
```

## Running it

Requirements: JDK 21 and Docker.

**Local (app on your machine, database in Docker)**

```bash
docker compose up -d        # starts PostgreSQL
./mvnw spring-boot:run      # API on http://localhost:8080
```

Default datasource (see `application.properties`): `jdbc:postgresql://localhost:5432/core_bank`, user `admin`. These are local development credentials only.

## Tests

```bash
./mvnw test
```

15 unit tests (JUnit 5 + Mockito) cover the service layer: login (success, unknown email, wrong password), deposits, withdrawals (ownership, insufficient balance, invalid amount), transfers (success, same account, not the owner) and account creation. `contextLoads` needs a running PostgreSQL.

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs the build and tests against a PostgreSQL service on every push and pull request.

## Known limitations

This is a learning project, not production banking software. Things I know are missing:

- The JWT signing secret is in the source code; it should come from an environment variable or a secret store.
- Anyone can register with the `ADMIN` role, because the role is taken from the sign-up request.
- `GET /api/accounts/{id}` does not check ownership.
- Locking and transaction atomicity are not covered by automated tests (the unit tests mock the repositories); an integration test against a real PostgreSQL (e.g. Testcontainers) with concurrent requests is the next step.
- Schema is managed by `ddl-auto=update`; a migration tool such as Flyway would be the proper approach.
- Account ids are chosen by the client.

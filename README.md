# Core Bank API

[![CI](https://github.com/DiogoSantiago22/core_bank_api/actions/workflows/ci.yml/badge.svg)](https://github.com/DiogoSantiago22/core_bank_api/actions/workflows/ci.yml)

A REST API for basic banking operations (accounts, deposits, withdrawals, transfers and transaction history), built with **Java 21, Spring Boot 4 and PostgreSQL**.

I built it to learn the Spring ecosystem outside of coursework: layered architecture, JPA, stateless JWT authentication and, mainly, how to keep money operations correct under concurrent access.

## Features

- Account sign-up (all accounts created via the API are assigned the `CLIENT` role by default) and login (passwords hashed with **BCrypt**, **JWT** access tokens with role claims, stateless sessions).
- Deposit, withdraw and **transfer** between accounts, each recorded as a transaction.
- Transaction history per account, newest first.
- Ownership and RBAC checks: a `CLIENT` can only read, deposit into, withdraw from, transfer from and view the transaction history of their own account, while an `ADMIN` can read any account.
- Interactive API documentation via **Swagger UI / OpenAPI 3** (`http://localhost:8080/swagger-ui.html`).
- Consistent JSON error responses for application errors (`400`, `401` on login, `403`, `404`) through a global exception handler. Requests without a valid token get a bare `401` from a custom authentication entry point.
- Request validation with Bean Validation (`@Valid`, `@NotBlank`, `@Positive`, `@Digits`).

## Stack

| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 4, Spring Web, Spring Data JPA (Hibernate) |
| Security | Spring Security, JWT (`com.auth0:java-jwt`), BCrypt |
| Database | PostgreSQL 16 |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL), H2 |
| Packaging | Maven (wrapper included), Docker, Docker Compose |

## Architecture

```text
controller  ->  service  ->  repository  ->  PostgreSQL
   |               |
  dto          security (JwtService, JwtAuthenticationFilter)
   |
exception (GlobalExceptionHandler)
```

- `controller`: HTTP layer, converts entities to response DTOs.
- `service`: business rules, transactions, ownership and role checks.
- `repository`: Spring Data JPA repositories.
- `dto`: Java `record`s for requests and responses, with validation annotations.
- `security`: JWT creation/verification (including role claims) and the filter that authenticates each request.

## Design decisions

**Money is `BigDecimal`**, never `double`, so there are no floating-point rounding errors, enforced with 2 decimal places (`@Digits(integer = 15, fraction = 2)`).

**Concurrency.** Two simultaneous withdrawals on the same account could both read the same balance and overdraw it. Balance-changing operations therefore load the account with a **pessimistic write lock** (`SELECT ... FOR UPDATE`, via `findByIdForUpdate`) inside a `@Transactional` method.

**Deadlock prevention in transfers.** A transfer locks two accounts. If A->B and B->A run at the same time and each locks its own source first, they deadlock. Transfers instead acquire the locks **in a fixed order (by account id)**, regardless of direction.

**Atomic transfers.** The debit, the credit and both transaction records happen in a single database transaction, so a failure rolls everything back.

**Stateless auth.** No server-side session: the client sends `Authorization: Bearer <token>`, and on every request the filter verifies the signature and expiry and reads the role claim to build the authorities.

## API

Public: `POST /api/accounts` (sign-up), `POST /api/auth/login` and Swagger UI (`http://localhost:8080/swagger-ui.html`). Everything else requires a `Bearer` token.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/accounts` | Create an account (`id`, `ownerName`, `email`, `password`; assigned `CLIENT` role by default) |
| `POST` | `/api/auth/login` | Log in with `email` and `password`, returns `{ "token": "..." }` |
| `GET` | `/api/accounts/{id}` | Get an account (owner or `ADMIN` only) |
| `PUT` | `/api/accounts/{id}/deposit` | Deposit into own account (`{ "amount": 50.00 }`) |
| `PUT` | `/api/accounts/{id}/withdraw` | Withdraw from own account (`{ "amount": 20.00 }`) |
| `POST` | `/api/accounts/transfer` | Transfer between accounts (`{ "fromId", "toId", "amount" }`) |
| `GET` | `/api/accounts/{id}/transactions` | Transaction history (owner only) |

### Example

```bash
# create an account
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"id":"100","ownerName":"Ana Silva","email":"ana@mail.com","password":"changeme"}'

# log in and use the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@mail.com","password":"changeme"}' | jq -r .token)

# deposit money
curl -X PUT http://localhost:8080/api/accounts/100/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00}'
```

### Error responses

Application errors share one JSON format:

```json
{
  "timestamp": "...",
  "status": 403,
  "error": "Forbidden",
  "message": "Not your account",
  "path": "/api/accounts/100/withdraw"
}
```

A missing or invalid token on a protected endpoint returns `401` with an empty body.

## Running it

### Requirements

- JDK 21
- Docker
- Docker Compose

### Local development

Run the application locally using the `dev` profile, with PostgreSQL running in Docker.

```bash
# start PostgreSQL
docker compose up -d db

# start the API with the dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`application-dev.properties`) provides a local development `jwt.secret`.

Once running, Swagger UI is available at:

`http://localhost:8080/swagger-ui.html`

### Full stack with Docker Compose

`JWT_SECRET` has no fallback in `application.properties` and must be provided at runtime (at least 32 characters for HMAC-SHA256).

A PostgreSQL healthcheck ensures the API container starts only after the database is ready.

```bash
# build the application
./mvnw clean package -DskipTests

# start the full stack
JWT_SECRET="your_production_secret_with_at_least_32_characters" docker compose up --build
```

Never commit production secrets to the repository.

## Tests

Run the test suite with (Docker must be running, because the integration tests start a PostgreSQL container):

```bash
./mvnw test
```

### Unit tests

**18 unit tests** (`AccountServiceTest`, JUnit 5 + Mockito) cover the service layer:

- **Login:** success, unknown email and wrong password.
- **Deposits:** success, negative amount, non-existent account and ownership.
- **Withdrawals:** success, ownership and insufficient balance.
- **Transfers:** success, same account and ownership.
- **Account creation:** success and duplicate ID.
- **Account lookup:** owner access, `ADMIN` access and forbidden access for other clients.

### Integration tests

**2 integration tests** (`ConcurrencyIntegrationTest`, Testcontainers with PostgreSQL 16) verify:

- Balance integrity under 100 concurrent transfers.
- Deadlock prevention under simultaneous bidirectional transfers (`A -> B` and `B -> A`).

The bidirectional test was checked by removing the fixed lock ordering and confirming that it fails.

### Application context test

**`CoreBancarioApplicationTests`** runs against an in-memory H2 database to verify that the Spring application context loads successfully.

### Continuous Integration (CI)

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs the build and full test suite (including Testcontainers PostgreSQL) on every push and pull request.

## Known limitations

This is a learning project, not production banking software. Remaining limitations:

- **Database migrations:** The schema is managed by `ddl-auto=update`; a migration tool such as Flyway or Liquibase would be the proper approach.
- **Client-generated account IDs:** Account IDs are chosen by the client rather than generated by the server.
- **Admin management:** There is no endpoint to create or promote an `ADMIN` account. Administrative privileges must be assigned directly in the database.
- **Authentication protection:** There is no rate limiting on login, nor refresh tokens or token revocation before the 24-hour expiry.
- **JWT role updates:** Role changes in the database only take effect on the user's next login, when a new JWT is issued.
- **Transaction history:** `TransactionType.TRANSFER` is currently unused. Transfers record paired `WITHDRAW` and `DEPOSIT` entries, and transaction history does not store the counterparty account.
- **Database credentials:** Credentials in `application.properties` and `docker-compose.yml` are intended for local development only.

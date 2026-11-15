# Core Bank API

A REST API for basic banking operations (accounts, deposits, withdrawals, transfers and transaction history), built with **Java 21, Spring Boot 4 and PostgreSQL**.

I built it to learn the Spring ecosystem outside of coursework: layered architecture, JPA, stateless JWT authentication and, mainly, how to keep money operations correct under concurrent access.

## Features

- Account sign-up (all accounts created via the API are assigned the `CLIENT` role by default) and login (passwords hashed with **BCrypt**, **JWT** access tokens with role claims, stateless sessions).
- Deposit, withdraw and **transfer** between accounts, each recorded as a transaction.
- Transaction history per account, newest first.
- Ownership and RBAC checks: a `CLIENT` can only read, withdraw from, transfer from and view the transaction history of their own account, while an `ADMIN` can read any account.
- Consistent error responses (`400`, `401`, `403`, `404`) through a global exception handler and custom authentication entry point.
- Request validation with Bean Validation (`@Valid`, `@NotBlank`, `@Positive`, `@Digits`).

## Stack

| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 4, Spring Web, Spring Data JPA (Hibernate) |
| Security | Spring Security, JWT (`com.auth0:java-jwt`), BCrypt |
| Database | PostgreSQL 16 |
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
- `security`: JWT creation/validation (including role claims) and the filter that authenticates each request.

## Design decisions

**Money is `BigDecimal`**, never `double`, so there are no floating-point rounding errors, enforced with 2 decimal places (`@Digits(integer = 15, fraction = 2)`).

**Concurrency.** Two simultaneous withdrawals on the same account could both read the same balance and overdraw it. Balance-changing operations therefore load the account with a **pessimistic write lock** (`SELECT ... FOR UPDATE`, via `findByIdForUpdate`) inside a `@Transactional` method.

**Deadlock prevention in transfers.** A transfer locks two accounts. If A->B and B->A run at the same time and each locks its own source first, they deadlock. Transfers instead acquire the locks **in a fixed order (by account id)**, regardless of direction.

**Atomic transfers.** The debit, the credit and both transaction records happen in a single database transaction, so a failure rolls everything back.

**Stateless auth.** No server-side session: the client sends `Authorization: Bearer <token>` and the filter validates the signature, expiry and role claim on every request.

## API

Public: `POST /api/accounts` (sign-up) and `POST /api/auth/login`. Everything else requires a `Bearer` token.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/accounts` | Create an account (`id`, `ownerName`, `email`, `password`; assigned `CLIENT` role by default) |
| `POST` | `/api/auth/login` | Log in with `email` and `password`, returns `{ "token": "..." }` |
| `GET` | `/api/accounts/{id}` | Get an account (owner or `ADMIN` only) |
| `PUT` | `/api/accounts/{id}/deposit` | Deposit (`{ "amount": 50.00 }`) |
| `PUT` | `/api/accounts/{id}/withdraw` | Withdraw (`{ "amount": 20.00 }`) |
| `POST` | `/api/accounts/transfer` | Transfer (`{ "fromId", "toId", "amount" }`) |
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

curl -X PUT http://localhost:8080/api/accounts/100/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount": 50.00}'
```

### Error responses

Errors share one format:

```json
{
  "timestamp": "...",
  "status": 403,
  "error": "Forbidden",
  "message": "Not your account",
  "path": "/api/accounts/100/withdraw"
}
```

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

The API will be available at:

`http://localhost:8080`

The `dev` profile (`application-dev.properties`) provides a local development `jwt.secret`.

### Full stack with Docker Compose

`JWT_SECRET` has no fallback in `application.properties` and must be provided at runtime (at least 32 characters for HMAC-SHA256).

```bash
# build the application
./mvnw clean package -DskipTests

# start the full stack
JWT_SECRET="your_production_secret_with_at_least_32_characters" docker compose up --build
```

Never commit production secrets to the repository.

## Tests

Run the test suite with:

```bash
./mvnw test
```

### Unit tests

- **17 unit tests** (`AccountServiceTest`, JUnit 5 + Mockito) cover the service layer:
    - Login (success, unknown email, wrong password).
    - Deposits.
    - Withdrawals (ownership, insufficient balance, invalid amount).
    - Transfers (success, same account, not the owner).
    - Account creation.
    - Account lookup (owner, `ADMIN` access, forbidden for other clients).

### Integration tests

- **2 integration tests** (`ConcurrencyIntegrationTest`, Testcontainers with PostgreSQL 16) verify:
    - Balance integrity under 100 concurrent transfers.
    - Deadlock prevention under simultaneous bidirectional transfers (`A -> B` and `B -> A`).

### Application context test

- **`CoreBancarioApplicationTests`** runs against an in-memory H2 database to verify that the Spring application context loads successfully.

### Continuous Integration (CI)

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs the build and full test suite (including Testcontainers PostgreSQL) on every push and pull request.

## Known limitations

This is a learning project, not production banking software. Remaining limitations:

- Schema is managed by `ddl-auto=update`; a migration tool such as Flyway would be the proper approach.
- Account IDs are chosen by the client.

## Finalizing Phase 5

After updating the `README.md`, commit and push the changes:

```powershell
git add README.md
git commit -m "docs(readme): align documentation with Spring Boot 4, RBAC ownership, and integration tests"
git push origin main
```
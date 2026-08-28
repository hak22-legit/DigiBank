# DigiBank — Enterprise Banking System

A console-based **Enterprise Banking and Financial Management System** built in Java, simulating real-world backend banking engineering: ACID-safe transactions, concurrency control, role-based access control, and personal finance tools.

> **Type:** Educational FinTech simulation (not connected to real financial institutions)
> **Interface:** Java console application — no web frontend, built on native JDBC
> **Status:** Actively in development — 11 of 26 planned phases complete

---

## Table of Contents

1. [Project Objective](#project-objective)
2. [Technology Stack](#technology-stack)
3. [Architecture](#architecture)
4. [Database Design](#database-design)
5. [Features Implemented](#features-implemented)
6. [Core Engineering Principles](#core-engineering-principles)
7. [Project Structure](#project-structure)
8. [Development Roadmap](#development-roadmap)
9. [Setup Instructions](#setup-instructions)

---

## Project Objective

DigiBank simulates a full banking system while providing financial insights, personal finance management, and lending services — demonstrating professional backend engineering practices: database design, transaction integrity, concurrency control, security, and clean layered architecture.

The project is built **incrementally, phase by phase**, with each phase fully implemented and tested before the next begins — mirroring how real software teams ship features iteratively rather than all at once.

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Language / Build** | Java 17, Maven |
| **Database** | PostgreSQL (hosted on [Neon](https://neon.tech), serverless cloud Postgres) |
| **Data Access** | Native JDBC (no ORM), HikariCP connection pooling |
| **Migrations** | Flyway |
| **Security** | jBCrypt (password hashing), PreparedStatement (SQL injection prevention) |
| **Code Quality** | Lombok |
| **Logging** | SLF4J + Logback |
| **Testing** | JUnit 5, Mockito, Testcontainers *(planned)* |

**Deliberately excluded:** Spring Boot, Hibernate/JPA, REST APIs, web frontend. This project is built on raw JDBC by design — the goal is to demonstrate low-level understanding of connection pooling, transaction management, and SQL, rather than relying on framework abstractions.

---

## Architecture

```
Console View → Controller → Service → Repository (DAO) → JDBC → PostgreSQL
```

**Layering rules enforced throughout the codebase:**
- Business logic and authorization live **only** in the Service layer — never in the console UI or the Repository layer.
- Repositories are CRUD-only — no business rules, no validation logic.
- Every repository follows an **Interface + Implementation** split, enabling clean unit testing via mocking.
- Single-record lookups return `Optional<T>`, never `null`, to force explicit handling of "not found" cases.

---

## Database Design

**11 normalized tables** (~3NF), hosted on PostgreSQL:

```
users, admins, accounts, categories, transactions,
loans, loan_payments, saving_goals, budgets,
audit_logs, fraud_alerts
```

### Key design decisions

- **Monetary values**: `NUMERIC(19,4)` in PostgreSQL, mapped to `BigDecimal` in Java — never `double`/`float`, to avoid floating-point rounding errors in financial calculations.
- **IDs**: `BIGSERIAL` in PostgreSQL, mapped to `Long` in Java.
- **Idempotency**: `transactions.idempotency_key` is `UUID NOT NULL UNIQUE` — duplicate transfer protection is enforced at the database level, not just in application code.
- **Data integrity via CHECK constraints**: valid values for status/type fields (e.g. account status, transaction type) are enforced by the database itself as a safety net independent of the Java layer.
- **Currency**: accounts and transactions carry a `Currency` (USD or KHR), but currency conversion is intentionally out of scope — transfers require matching currencies.

### Entity relationships

```
users ─┬─ accounts ── transactions ── categories
       ├─ loans ── loan_payments
       ├─ saving_goals
       └─ budgets ── categories

admins ─┬─ loans (approved_by)
        ├─ audit_logs
        └─ fraud_alerts
```

---

## Features Implemented

### Authentication & Session Management
- Secure registration and login with **BCrypt** password hashing (work factor 12)
- Generic error messaging (`"Invalid username or password"`) to prevent username enumeration attacks
- Session tracking via a singleton `SessionManager`, suited for a single-user console application
- **A default checking account (USD) is automatically created for every new user upon registration** — no manual setup required

### Account Management
- Users can hold **multiple accounts** of different types (`CHECKING`, `SAVINGS`, `LOAN`) and currencies (`USD`, `KHR`)
- Auto-generated, collision-checked account numbers (format: `DGB-XXXXXXXXX`)
- Strict ownership enforcement — a user can only view or act on accounts they own

### Deposit
- Full **ACID transaction**: pessimistic row locking (`SELECT ... FOR UPDATE`), balance update, and transaction record creation happen atomically
- Enforces amount limits (`> 0` and `<= 1,000,000`), account status (`ACTIVE`), and currency matching
- Automatic rollback on any failure — no partial state is ever persisted

### Withdrawal
- Same ACID guarantees as Deposit, plus **strict no-overdraft enforcement** — a withdrawal is rejected if it would bring the balance below zero
- All balance comparisons use `BigDecimal.compareTo()` to avoid floating-point/scale comparison bugs

### Fund Transfer *(the most complex feature in the system)*
- **Deterministic lock ordering**: when locking two accounts for a transfer, the system always locks the lower `account_id` first — this prevents deadlocks that could otherwise occur when two transfers move money in opposite directions between the same pair of accounts simultaneously
- **Two-layer idempotency protection**:
    1. A pre-check against the database before starting the transaction — if a transfer with the same idempotency key was already processed, the original result is returned instead of transferring again
    2. A race-condition safety net: if two identical requests somehow race past the pre-check, the database's `UNIQUE` constraint on the idempotency key rejects the duplicate at the SQL level, and the system gracefully returns the already-completed transaction instead of throwing an error
- Supports both **self-transfers** (between a user's own accounts) and transfers to any other user's account
- Enforces matching currencies between sender and receiver (no conversion)
- One transaction row represents both legs of a transfer (`account_id` = sender, `related_account_id` = receiver)

### Transaction History
- View, and filter, all transactions for an account
- Transactions are tagged as **Income** or **Outcome** relative to the account being viewed — for example, the same transfer is an *Outcome* for the sender and an *Income* for the receiver
- History correctly includes incoming transfers, not just transactions the account directly initiated

---

## Core Engineering Principles

These principles are applied consistently across every feature, not just called out once:

| Principle | How it's applied |
|---|---|
| **ACID Transactions** | Every balance-changing operation runs inside an explicit JDBC transaction (`setAutoCommit(false)` → `commit()` / `rollback()`) |
| **Concurrency Control** | Pessimistic locking (`SELECT ... FOR UPDATE`) prevents race conditions and double-spending on concurrent operations against the same account |
| **Idempotency** | Transfers are protected against duplicate execution from network retries or double-submissions, enforced at both the application and database level |
| **Authorization in the Service layer** | Every sensitive operation checks account ownership in code — never relies on hiding options in the UI |
| **SQL Injection Prevention** | 100% `PreparedStatement` usage; no string-concatenated queries anywhere in the codebase |
| **Precise Financial Math** | `BigDecimal` and `NUMERIC(19,4)` throughout — `double`/`float` are never used for money |

---

## Project Structure

```
com.bank
├── config          → application configuration
├── database        → HikariCP datasource, connection management
├── model           → domain entities (User, Account, Transaction, etc.)
├── enums           → type-safe constants (AccountStatus, Currency, etc.)
├── repository      → data access layer (interface + implementation per entity)
├── service         → business logic and transaction orchestration
├── security        → password hashing, session management
├── exception       → custom exception hierarchy
└── Main.java
```

---

## Setup Instructions

1. **Prerequisites**: Java 17+, Maven, a PostgreSQL database (this project uses [Neon](https://neon.tech), no local database required)
2. Copy `src/main/resources/application.properties.example` to `application.properties` and fill in your database credentials
3. Run migrations: `mvn flyway:migrate`
4. Build and run: `mvn clean compile` then run `com.bank.Main`

> **Note:** `application.properties` is excluded from version control (`.gitignore`) since it contains database credentials. Never commit real credentials to this repository.

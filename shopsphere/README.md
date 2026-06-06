# ShopSphere — Services Directory

This directory contains all microservices, the shared Docker Compose infrastructure, and the parent Maven POM. All `mvn -pl` commands must be run from here.

See the [root README](../README.md) for the full architecture overview, frontend integration guides, and API reference.

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

---

## Running Locally

### 1. Start infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL (5432), Redis (6379), Kafka (9092), and Zookeeper.

### 2. Build all modules

```bash
mvn clean install -DskipTests
```

### 3. Start services

Each in a separate terminal. **Start in this order** — Eureka and Config Server must be up before any other service starts.

```bash
mvn -pl service-registry     spring-boot:run   # Eureka        :8761
mvn -pl config-service       spring-boot:run   # Config        :8888
mvn -pl api-gateway          spring-boot:run   # Gateway       :8080
mvn -pl user-service         spring-boot:run   # Auth          :8082
mvn -pl product-service      spring-boot:run   # Catalog       :8081
mvn -pl cart-service         spring-boot:run   # Cart          :8083
mvn -pl order-service        spring-boot:run   # Orders        :8084
mvn -pl inventory-service    spring-boot:run   # Stock         :8085
mvn -pl payment-service      spring-boot:run   # Payments      :8086
mvn -pl notification-service spring-boot:run   # Notifications :8087
```

All frontend traffic goes to the **gateway at `http://localhost:8080`**.

---

## Modules

| Module | Port | Description |
|---|---|---|
| service-registry | 8761 | Eureka server — service discovery |
| config-service | 8888 | Centralized Spring Cloud Config |
| api-gateway | 8080 | Single entry point for all clients |
| user-service | 8082 | Registration, login, JWT |
| product-service | 8081 | Catalog, search, categories |
| cart-service | 8083 | Cart operations (Redis-backed) |
| order-service | 8084 | Order lifecycle |
| inventory-service | 8085 | Stock tracking, availability |
| payment-service | 8086 | Payment simulation |
| notification-service | 8087 | Notification simulation |
| common-lib | — | Shared DTOs and Kafka event types |

---

## Resetting State

Flush Redis and truncate all tables between test runs:

```bash
docker exec -i shopsphere-redis redis-cli FLUSHDB > /dev/null
docker exec -i shopsphere-postgres psql -U shopsphere -d shopsphere -c "
  TRUNCATE TABLE shopsphere_orders.orders RESTART IDENTITY CASCADE;
  TRUNCATE TABLE shopsphere_payments.payments RESTART IDENTITY CASCADE;
  TRUNCATE TABLE shopsphere_inventory.inventory RESTART IDENTITY CASCADE;
  TRUNCATE TABLE shopsphere_inventory.processed_events RESTART IDENTITY CASCADE;
  TRUNCATE TABLE shopsphere_notifications.notifications RESTART IDENTITY CASCADE;
" > /dev/null
```

> `shopsphere_notifications` is created by the notification-service on first startup. If it hasn't run yet, omit that line.

---

## Database Schemas

Each service owns its own schema in the shared `shopsphere` database.

| Service | Schema |
|---|---|
| user-service | `shopsphere_users` |
| product-service | `public` |
| order-service | `shopsphere_orders` |
| inventory-service | `shopsphere_inventory` |
| notification-service | `shopsphere_notifications` |
| payment-service | `shopsphere_payments` |

Schemas are created on first service startup via Hikari `connection-init-sql`. Tables are managed by Hibernate `ddl-auto: update`.

---

## API Docs

| Service | Swagger UI | OpenAPI Spec |
|---|---|---|
| Product | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| User | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| Cart | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| Order | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |
| Inventory | http://localhost:8085/swagger-ui.html | http://localhost:8085/v3/api-docs |

To authenticate in Swagger UI: call `POST /api/v1/auth/login`, copy the token, click **Authorize**, paste the token (no `Bearer` prefix).

---

## Eureka Dashboard

http://localhost:8761 — shows all registered services and their live status.

---

## Docker Build

Each service has a `Dockerfile` that builds independently from the parent POM. The `<modules>` block is stripped inside the build container so Maven only compiles the one service being built.

```bash
# Build and start everything with Docker Compose
docker compose up --build
```

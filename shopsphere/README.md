# ShopSphere — Microservices E-Commerce Backend

A production-style Java microservices backend built with Spring Boot 3, Spring Cloud, PostgreSQL, Redis, Kafka, and Docker.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21+ (tested on Java 26) |
| Framework | Spring Boot 3.3.5 |
| Service discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway |
| Databases | PostgreSQL 16 (JPA/Hibernate) · Redis 7 (cart) |
| Messaging | Apache Kafka |
| Build | Maven 3.9+ multi-module |
| Containers | Docker Compose |
| Docs | SpringDoc OpenAPI 3 / Swagger UI |

## Modules

| Module | Port | Description |
|---|---|---|
| service-registry | 8761 | Eureka server |
| config-service | 8888 | Spring Cloud Config |
| api-gateway | 8080 | Single entry point |
| user-service | 8082 | Auth, JWT, roles |
| product-service | 8081 | Catalog, search, categories |
| cart-service | 8083 | Cart operations (Redis-backed) |
| order-service | 8084 | Order lifecycle |
| inventory-service | 8085 | Stock tracking |
| payment-service | — | Simulated payments |
| notification-service | — | Simulated notifications |
| common-lib | — | Shared DTOs / events |

## Prerequisites

- Java 21+ (Java 26 works — Lombok 1.18.38 is configured)
- Maven 3.9+
- Docker + Docker Compose

## Running

### 1. Start infrastructure

```bash
docker compose up -d
```

### 2. Build all modules

```bash
mvn clean install -DskipTests
```

### 3. Start each service (separate terminals)

```bash
mvn -pl user-service      spring-boot:run
mvn -pl product-service   spring-boot:run
mvn -pl cart-service      spring-boot:run
mvn -pl order-service     spring-boot:run
mvn -pl inventory-service spring-boot:run
```

> All `-pl` commands must be run from this directory (`shopsphere/`), where the parent `pom.xml` lives.

## Database Schemas

Each service owns its own PostgreSQL schema inside the shared `shopsphere` database:

| Service | Schema |
|---|---|
| user-service | `shopsphere_users` |
| product-service | `public` (default) |
| order-service | `shopsphere_orders` |
| inventory-service | `shopsphere_inventory` |

Schemas are created automatically on first service startup. Tables are managed by Hibernate `ddl-auto: update`.

## API Documentation (Swagger / OpenAPI)

Each service exposes Swagger UI at `/swagger-ui.html` and a raw spec at `/v3/api-docs`.

| Service | Swagger UI | OpenAPI Spec |
|---|---|---|
| Product | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| User | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| Cart | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| Order | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |
| Inventory | http://localhost:8085/swagger-ui.html | http://localhost:8085/v3/api-docs |

### Authenticating from Swagger UI

1. Open the User Service Swagger UI.
2. Call `POST /api/v1/auth/register` or `POST /api/v1/auth/login` and copy the `token`.
3. Click the green **Authorize** button (top right).
4. Paste the token (no "Bearer " prefix) and close the dialog.
5. Protected endpoints will now succeed.

## Endpoint Cheat Sheet

### Auth (public)

```
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### Users (JWT required)

```
GET /api/v1/users/me
GET /api/v1/users/health   (public)
```

### Products

```
POST   /api/v1/products
GET    /api/v1/products?name=&categoryId=&minPrice=&maxPrice=&active=&inStock=&page=&size=&sort=
GET    /api/v1/products/{id}
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

### Categories

```
POST /api/v1/categories
GET  /api/v1/categories
```

### Cart (X-User-Id header required)

```
GET    /api/v1/carts
POST   /api/v1/carts/items
PUT    /api/v1/carts/items/{productId}
DELETE /api/v1/carts/items/{productId}
DELETE /api/v1/carts/clear
```

### Orders (X-User-Id header required)

```
POST /api/v1/orders
GET  /api/v1/orders/my-orders
GET  /api/v1/orders/{orderId}
```

### Inventory

```
POST /api/v1/inventory
GET  /api/v1/inventory/{productId}
GET  /api/v1/inventory/low-stock
POST /api/v1/inventory/check-availability
PUT  /api/v1/inventory/{productId}
```

## Status

- US1  done — Base project structure
- US2  done — Product Service skeleton
- US3  done — Product CRUD APIs
- US4  done — Category, search & filtering
- US5  done — User Service skeleton
- US6  done — User registration
- US7  done — Login + JWT auth
- US8  done — Swagger documentation
- US9  done — Cart Service (Redis-backed)
- US10 done — Cart CRUD operations
- US11 done — Order Service skeleton
- US12 done — Place order from cart
- US13 done — View order history
- US14 done — Inventory Service skeleton
- US15 done — Stock init & updates
- US16 done — Batch availability check

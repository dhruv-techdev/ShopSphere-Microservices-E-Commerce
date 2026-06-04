# ShopSphere — Microservices E-Commerce Backend

A production-style Java microservices backend built with Spring Boot 3, Spring Cloud, PostgreSQL, Redis, Kafka, and Docker.

## Modules
- service-registry — Eureka server
- config-service — Spring Cloud Config
- api-gateway — Spring Cloud Gateway (single entry point)
- user-service — auth, JWT, roles
- product-service — catalog, search, categories
- cart-service — cart ops (Redis-backed)
- order-service — order lifecycle
- payment-service — simulated payments
- inventory-service — stock tracking
- notification-service — simulated notifications
- common-lib — shared DTOs / events

## Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

## Running

### 1. Start infrastructure
    docker compose up -d

### 2. Build all modules
    mvn clean install -DskipTests

### 3. Start each service (separate terminals)
    mvn -pl product-service spring-boot:run
    mvn -pl user-service    spring-boot:run

## API Documentation (Swagger / OpenAPI)

Each service exposes interactive Swagger UI and a raw OpenAPI 3 spec.

### Product Service (port 8081)
- Swagger UI:   http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/v3/api-docs

### User Service (port 8082)
- Swagger UI:   http://localhost:8082/swagger-ui.html
- OpenAPI spec: http://localhost:8082/v3/api-docs

### Authenticating from Swagger UI
1. Open the User Service Swagger UI.
2. Call `POST /api/v1/auth/register` or `POST /api/v1/auth/login` and copy the `token`.
3. Click the green **Authorize** button (top right).
4. Paste the token (no "Bearer" prefix needed) and close the dialog.
5. Protected endpoints such as `GET /api/v1/users/me` will now succeed.

## Endpoint Cheat Sheet

### Auth (public)
- POST /api/v1/auth/register
- POST /api/v1/auth/login

### Users (JWT required)
- GET  /api/v1/users/me
- GET  /api/v1/users/health   (public)

### Products
- POST   /api/v1/products
- GET    /api/v1/products?name=&categoryId=&minPrice=&maxPrice=&active=&inStock=&page=&size=&sort=
- GET    /api/v1/products/{id}
- PUT    /api/v1/products/{id}
- DELETE /api/v1/products/{id}

### Categories
- POST /api/v1/categories
- GET  /api/v1/categories

## Status
- US1  done — Base project structure
- US2  done — Product Service skeleton
- US3  done — Product CRUD APIs
- US4  done — Category, search & filtering
- US5  done — User Service skeleton
- US6  done — User registration
- US7  done — Login + JWT auth
- US8  done — Swagger documentation
- US9  next — Cart Service

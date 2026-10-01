# ShopSphere — Production-Ready E-Commerce Microservices Backend

A production-grade e-commerce backend built as event-driven microservices (Java / Spring Boot, plus a .NET shipping service), with an Angular admin console. Put any frontend in front of it — React, Next.js, Vue, Angular, Flutter, React Native, or plain HTTP — and you have a complete store.

All client traffic goes through a single API Gateway on port `8080`. The gateway verifies the JWT, enforces roles, and forwards the caller's identity to the services. Services register with Eureka, pull configuration from Config Server, and talk to each other asynchronously over Kafka.

---

## Highlights

- **One entry point** — every request goes to `localhost:8080` (or your domain).
- **JWT auth with refresh tokens** — register, verify email, log in, refresh, log out, reset password.
- **Identity is set by the gateway** — the gateway derives `X-User-Id` / `X-User-Role` from the token and strips any client-supplied copies, so callers can't impersonate another user.
- **Role-based access** — catalog writes and `/api/v1/admin/**` require `ADMIN`, checked at the gateway and again in each service (`security-lib`).
- **Event-driven order flow** — order → stock reservation → payment → shipment → notifications, all over Kafka, with idempotent consumers.
- **Self-healing workflows** — unpaid reservations expire and their orders are cancelled automatically; failed emails are retried with backoff and dead-lettered.
- **Real email delivery** — SMTP or SendGrid, HTML templates; Mailpit catches every email locally.
- **Schema-per-service PostgreSQL** with Flyway migrations where the schema is evolving.
- **Polyglot** — the shipping service is ASP.NET Core 8, wired into the same Eureka, Config Server and Kafka.
- **CI on every PR** — Java and .NET builds and tests, plus Docker image builds.

---

## Architecture

```
 Clients (web, mobile)          Admin console (Angular) :4200
            │                              │
            └──────────────┬───────────────┘
                           ▼
                  API Gateway :8080        JWT check · role rules · identity headers
                           │
  ┌────────────────────────┼─────────────────────────────────────┐
  │ user-service         :8082   auth, email verification, password reset
  │ product-service      :8081   catalog, categories, search
  │ cart-service         :8083   Redis-backed cart
  │ order-service        :8084   order lifecycle, admin order API, cancellation
  │ inventory-service    :8085   stock, reservations, expiry sweeper
  │ payment-service      :8086   payment simulation
  │ notification-service :8087   email delivery, retry, DLQ
  │ shipping-service     :8088   shipments, tracking numbers (.NET 8)
  └────────────────────────┼─────────────────────────────────────┘
                           │
  PostgreSQL :5432 · Redis :6379 · Kafka :9092 (+ ZooKeeper :2181)
  Eureka :8761 · Config Server :8888 · Mailpit :8025 (UI) / :1025 (SMTP)
```

### Order flow (Kafka)

```
order.created ─► inventory reserves stock
              ─► payment
                   ├─ payment.successful ─► order marked paid · inventory commits stock
                   │                      ─► shipping creates shipment ─► shipment.dispatched ─► shipment.delivered ─► order updated
                   └─ payment.failed     ─► order marked failed · inventory releases stock

reservation not paid in time ─► inventory.reservation-expired ─► order cancelled ─► order.cancelled
every customer-facing event  ─► notification-service ─► email   (failures ─► retry ─► notification.dlq)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Services | Java 21, Spring Boot 3.3.5 · ASP.NET Core 8 (shipping) |
| Gateway / discovery / config | Spring Cloud Gateway · Eureka · Spring Cloud Config (Steeltoe on .NET) |
| Data | PostgreSQL 16 (schema per service) · Redis 7 · Flyway / EF Core |
| Messaging | Apache Kafka (Confluent 7.6, ZooKeeper mode) |
| Auth | JWT (HS256) + refresh tokens, shared `security-lib` |
| Email | Thymeleaf templates · SMTP / SendGrid · Mailpit for local dev |
| Admin UI | Angular 18, served by nginx in Docker |
| Build | Maven 3.9 multi-module · .NET SDK 8 · npm |
| Tests | JUnit 5, Mockito, Testcontainers, embedded Kafka · xUnit · Karma |
| CI | GitHub Actions |
| API docs | SpringDoc OpenAPI 3 / Swagger UI |

---

## Repository Layout

```
.github/workflows/ci.yml     CI: Java, .NET and Docker build jobs
shopsphere/
  pom.xml                    Maven parent (all Java modules)
  docker-compose.yml         full local stack
  common-lib/                shared Kafka event classes and topic names
  security-lib/              shared JWT verification / role auto-configuration
  service-registry/          Eureka
  config-service/            Spring Cloud Config (configs in src/main/resources/config)
  api-gateway/
  user-service/  product-service/  cart-service/  order-service/
  inventory-service/  payment-service/  notification-service/
  shipping-service/          ASP.NET Core 8 (src/ + tests/)
  admin-app/                 Angular admin console
```

---

## Services & Ports

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | **8080** | Single entry point; JWT, roles, identity headers |
| product-service | 8081 | Products, categories, search |
| user-service | 8082 | Registration, login, refresh, email verification, password reset |
| cart-service | 8083 | Cart (Redis) |
| order-service | 8084 | Orders, admin order management, cancellation |
| inventory-service | 8085 | Stock levels, reservations, reservation expiry |
| payment-service | 8086 | Payment simulation |
| notification-service | 8087 | Email notifications, retry, dead-letter queue |
| shipping-service | 8088 | Shipments and tracking (.NET 8) |
| admin-app | 4200 | Angular admin console |
| service-registry | 8761 | Eureka dashboard |
| config-service | 8888 | Centralized configuration |
| Mailpit | 8025 / 1025 | Local email inbox (web UI) / SMTP |

---

## Quick Start (Docker — recommended)

### Prerequisites

- Docker Desktop
- Java 21 and Maven 3.9 (to build and test locally)
- .NET SDK 8 (only to work on `shipping-service`)
- Node.js 20+ (only to work on `admin-app`)

### Run the whole stack

```bash
cd shopsphere
docker compose up -d --build
docker ps --format '{{.Names}}\t{{.Status}}'
```

The first build takes a few minutes. When every container shows `Up`:

| What | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Admin console | http://localhost:4200 |
| Email inbox (Mailpit) | http://localhost:8025 |
| Eureka dashboard | http://localhost:8761 |

Rebuild a single service after a change:

```bash
docker compose up -d --build order-service
```

### Create an admin user

No admin is seeded. Register one, then confirm the email (click the link in Mailpit, or flip the flag directly):

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Admin","lastName":"User","email":"admin@shopsphere.local","password":"Admin@12345","role":"ADMIN"}'

docker exec shopsphere-postgres psql -U shopsphere -d shopsphere \
  -c "update shopsphere_users.users set email_verified = true where email = 'admin@shopsphere.local';"
```

Then sign in at http://localhost:4200.

> **Note:** public registration currently accepts `"role":"ADMIN"`. Fine for local development; restrict it before deploying anywhere public.

---

## Local Development

### Build and test

```bash
cd shopsphere
mvn clean install          # all Java modules, with tests
mvn -pl order-service -am test   # one service (plus the modules it depends on)
```

```bash
dotnet test shopsphere/shipping-service/tests/ShippingService.Tests/ShippingService.Tests.csproj
```

Integration tests use Testcontainers and need Docker running. If they are reported as *skipped* on a recent Docker Desktop, add `api.version=1.44` to `~/.docker-java.properties`.

### Admin console in dev mode

```bash
cd shopsphere/admin-app
npm install
npm start                  # http://localhost:4200, proxies /api to the gateway on :8080
```

Stop the `admin-app` container first (`docker compose stop admin-app`) — both use port 4200.

### Running a Java service from your IDE / terminal

Start the infrastructure and platform services in Docker, stop the one you're working on, and run it locally:

```bash
docker compose stop order-service
mvn -pl order-service spring-boot:run
```

---

## Using the API

### Auth flow

```
1. POST /api/v1/auth/register                     → account created, verification email sent
2. POST /api/v1/auth/email-verification/confirm   { token }   (link in the email)
3. POST /api/v1/auth/login                        → { token, refreshToken, expiresIn, userId, role }
4. Send  Authorization: Bearer <token>  on every protected request
5. POST /api/v1/auth/refresh  { refreshToken }    → new token pair when the access token expires
6. POST /api/v1/auth/logout   { refreshToken }
```

You only send the `Authorization` header. The gateway works out who the caller is and adds `X-User-Id` / `X-User-Role` itself; any values a client sends for these headers are discarded.

### Example (any JavaScript frontend)

```js
const BASE = 'http://localhost:8080';   // Android emulator: http://10.0.2.2:8080
let token = '';

export async function login(email, password) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  const data = await res.json();
  token = data.token;          // keep data.refreshToken to renew it
  return data;
}

const auth = () => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

export const getProducts = () => fetch(`${BASE}/api/v1/products`).then(r => r.json());
export const addToCart   = (productId, quantity) =>
  fetch(`${BASE}/api/v1/carts/items`, { method: 'POST', headers: auth(), body: JSON.stringify({ productId, quantity }) });
export const placeOrder  = body =>
  fetch(`${BASE}/api/v1/orders`, { method: 'POST', headers: auth(), body: JSON.stringify(body) }).then(r => r.json());
```

The same pattern works in any stack (Angular `HttpClient`, Axios, Flutter `http`, …). From a browser on another origin, configure CORS on the gateway for your frontend's domain.

---

## API Reference

All paths are called through the gateway (`http://localhost:8080`).

### Auth — public

```
POST /api/v1/auth/register                       { firstName, lastName, email, password }
POST /api/v1/auth/login                          { email, password }
POST /api/v1/auth/refresh                        { refreshToken }
POST /api/v1/auth/logout                         { refreshToken }
POST /api/v1/auth/email-verification/confirm
POST /api/v1/auth/email-verification/resend
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm
```

### Users — JWT

```
GET /api/v1/users/me
```

### Products & categories — GET is public, writes require ADMIN

```
GET    /api/v1/products?name=&categoryId=&minPrice=&maxPrice=&inStock=&page=&size=&sort=
GET    /api/v1/products/{id}
POST   /api/v1/products               PUT /api/v1/products/{id}       DELETE /api/v1/products/{id}
GET    /api/v1/categories             GET /api/v1/categories/{id}
POST   /api/v1/categories             PUT /api/v1/categories/{id}     DELETE /api/v1/categories/{id}
```

### Cart — JWT

```
GET    /api/v1/carts
POST   /api/v1/carts/items               { productId, quantity }
PUT    /api/v1/carts/items/{productId}   { quantity }
DELETE /api/v1/carts/items/{productId}
DELETE /api/v1/carts/clear
```

### Orders — JWT

```
POST /api/v1/orders                      { items, shippingAddress }
GET  /api/v1/orders/my-orders
GET  /api/v1/orders/{orderId}
```

### Admin orders — ADMIN

```
GET  /api/v1/admin/orders
GET  /api/v1/admin/orders/{orderId}
POST /api/v1/admin/orders/{orderId}/cancel
```

### Payments — JWT

```
POST /api/v1/payments/simulate
GET  /api/v1/payments
GET  /api/v1/payments/{paymentReference}
```

### Inventory — JWT

```
POST /api/v1/inventory                       { productId, quantity }
GET  /api/v1/inventory/{productId}
PUT  /api/v1/inventory/{productId}           { quantity }
GET  /api/v1/inventory/low-stock
POST /api/v1/inventory/check-availability    [{ productId, quantity }, ...]
```

### Shipments — JWT (owner only)

```
GET  /api/v1/shipments/{shipmentId}
GET  /api/v1/shipments/order/{orderId}
POST /api/v1/shipments/{shipmentId}/ship
POST /api/v1/shipments/{shipmentId}/deliver
POST /api/v1/shipments/{shipmentId}/cancel
```

### Notifications — JWT

```
GET /api/v1/notifications/user/{userId}
```

Every service also exposes a public `GET /api/v1/<service>/health`.

---

## API Docs (Swagger UI)

Each Java service serves Swagger UI at `/swagger-ui.html` and the raw spec at `/v3/api-docs`; the shipping service serves `/swagger`.

| Service | Swagger UI |
|---|---|
| Product | http://localhost:8081/swagger-ui.html |
| User | http://localhost:8082/swagger-ui.html |
| Cart | http://localhost:8083/swagger-ui.html |
| Order | http://localhost:8084/swagger-ui.html |
| Inventory | http://localhost:8085/swagger-ui.html |
| Shipping | http://localhost:8088/swagger |

To call protected endpoints: log in, copy `token`, click **Authorize**, paste it.

---

## Kafka Topics

| Topic | Published by | Consumed by |
|---|---|---|
| `order.created` | order | inventory, payment, notification |
| `order.cancelled` | order | notification |
| `payment.successful` | payment | order, inventory, shipping, notification |
| `payment.failed` | payment | order, inventory, notification |
| `inventory.low-stock` | inventory | notification |
| `inventory.reservation-expired` | inventory | order |
| `shipment.dispatched` / `shipment.delivered` | shipping | order, notification |
| `user.email-verification-requested` / `user.password-reset-requested` | user | notification |
| `notification.dlq` | notification | notification (marks permanently failed) |

Topic names live in `common-lib` (`Topics.java`); the .NET service mirrors them in `Messaging/Topics.cs`.

---

## Database

One PostgreSQL database (`shopsphere`), one schema per service:

| Service | Schema | Schema management |
|---|---|---|
| user-service | `shopsphere_users` | Hibernate |
| product-service | `public` | Hibernate |
| order-service | `shopsphere_orders` | Flyway (`db/migration`) |
| inventory-service | `shopsphere_inventory` | Hibernate |
| payment-service | `shopsphere_payments` | Hibernate |
| notification-service | `shopsphere_notifications` | Flyway (`db/migration`) |
| shipping-service | `shopsphere_shipping` | EF Core |

When you change an entity in a Flyway-managed service, add a new `V<n>__description.sql` migration — never edit one that has already run.

---

## Configuration

Shared settings live in `shopsphere/config-service/src/main/resources/config/<service>.yml`. Environment variables used by Docker Compose (see `shopsphere/.env.example`):

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/shopsphere
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
REDIS_HOST=<redis-host>
KAFKA_BOOTSTRAP=<kafka-host>:29092
EUREKA_URL=http://<eureka-host>:8761/eureka/
CONFIG_SERVER_URL=http://<config-host>:8888
JWT_SECRET=<at least 256 bits of randomness>
INTERNAL_API_TOKEN=<shared secret for service-to-service calls>
FRONTEND_BASE_URL=http://localhost:4200     # used in email links
AUTH_REQUIRE_VERIFIED_EMAIL=true
```

---

## CI

`.github/workflows/ci.yml` runs on pushes and pull requests to `main`:

1. **Java** — `mvn clean verify` across all modules (unit and integration tests).
2. **.NET** — restore, build and test `shipping-service`.
3. **Docker** — builds every service image (after both test jobs pass).

---

## Adding a New Service

New services don't have to be Java — `shipping-service` (.NET) is the reference for a non-Java service. To plug one in:

1. Put it in its own folder under `shopsphere/` with its own Dockerfile and tests.
2. Give it its own PostgreSQL schema (`shopsphere_<name>`) and its own migrations.
3. Communicate through Kafka topics (add the names to `common-lib/Topics.java`) or REST through the gateway — never read another service's tables.
4. Add a gateway route in `config-service/.../config/api-gateway.yml` and decide which paths are public, JWT-only or ADMIN.
5. Add it to `docker-compose.yml` and add a build/test job to `.github/workflows/ci.yml`.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `dependency kafka failed to start` / `NodeExistsException` | Kafka restarted before ZooKeeper dropped its old registration. The `kafka` service's startup command waits for it — make sure that block is still in `docker-compose.yml`, then `docker compose up -d` again. |
| `column ... does not exist` | The service never started, so its migration didn't run. Check `docker logs shopsphere-<service>`. |
| Integration tests show as *skipped* | Docker isn't reachable from Testcontainers — see **Build and test** above. |
| Login works on `:8082` but not through `:8080` | Check `docker logs shopsphere-gateway` for exceptions. |
| `#` lines in pasted commands fail in zsh | Run `setopt interactivecomments`, or add it to `~/.zshrc`. |

---

## Deploying to Production

The backend is environment-agnostic — point clients at your deployed gateway instead of `localhost:8080`.

| Platform | Notes |
|---|---|
| **AWS ECS / Fargate** | One task per service; RDS (Postgres), ElastiCache (Redis), MSK (Kafka) |
| **Kubernetes (GKE / EKS / AKS)** | One Deployment per service; Eureka optional with Kubernetes DNS |
| **Railway / Render** | Push Docker images; set connection strings as env vars |
| **DigitalOcean App Platform** | Multi-container app with managed Postgres and Redis |

Before going live: set a real `JWT_SECRET` and `INTERNAL_API_TOKEN`, configure SendGrid or SMTP for email, restrict ADMIN self-registration, and configure CORS for your frontend's domain.

# Getting Started

## Prerequisites

| Tool | Minimum Version |
|---|---|
| Java | 21 (tested on Java 26) |
| Maven | 3.9+ |
| Docker | 24+ |
| Docker Compose | 2.x (included with Docker Desktop) |

---

## 1. Start Infrastructure

From the `shopsphere/` directory:

```bash
docker compose up -d
```

This starts:
- PostgreSQL 16 on port `5432`
- Redis 7 on port `6379`
- Apache Kafka on port `9092`
- Zookeeper on port `2181`

Verify everything is healthy:

```bash
docker compose ps
```

---

## 2. Build All Modules

```bash
cd shopsphere
mvn clean install -DskipTests
```

---

## 3. Start Services

Open a separate terminal for each service. **Start in this order** — Eureka must register before other services, and Config Server must be up before services pull their config.

```bash
# Terminal 1
mvn -pl service-registry     spring-boot:run   # Eureka        :8761

# Terminal 2
mvn -pl config-service       spring-boot:run   # Config Server :8888

# Terminal 3 — wait for config-service to be UP before continuing
mvn -pl api-gateway          spring-boot:run   # Gateway       :8080

# Terminals 4-10 (order doesn't matter after registry+config are up)
mvn -pl user-service         spring-boot:run   # Auth          :8082
mvn -pl product-service      spring-boot:run   # Catalog       :8081
mvn -pl cart-service         spring-boot:run   # Cart          :8083
mvn -pl order-service        spring-boot:run   # Orders        :8084
mvn -pl inventory-service    spring-boot:run   # Stock         :8085
mvn -pl payment-service      spring-boot:run   # Payments      :8086
mvn -pl notification-service spring-boot:run   # Notifications :8087
```

---

## 4. Verify

Check all services registered with Eureka:

```bash
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" \
  | python3 -c "
import json, sys
apps = json.load(sys.stdin)['applications']['application']
for a in apps:
    print(a['name'], a['instance'][0]['port']['$'])
"
```

Run health checks through the gateway:

```bash
curl -s http://localhost:8080/api/v1/products/health | python3 -m json.tool
curl -s http://localhost:8080/api/v1/users/health    | python3 -m json.tool
curl -s http://localhost:8080/api/v1/orders/health   | python3 -m json.tool
```

---

## 5. First Request

Register a user and get a token:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"secret123","firstName":"Test","lastName":"User"}' \
  | python3 -m json.tool
```

Then login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"secret123"}' \
  | python3 -m json.tool
# → { "token": "...", "userId": 1, "role": "USER" }
```

Use the token for protected endpoints — see [authentication.md](./authentication.md).

---

## Eureka Dashboard

http://localhost:8761 — shows all registered services and their status.

## Swagger UI

Each service has interactive API docs at `/swagger-ui.html`:

| Service | URL |
|---|---|
| Product | http://localhost:8081/swagger-ui.html |
| User | http://localhost:8082/swagger-ui.html |
| Cart | http://localhost:8083/swagger-ui.html |
| Order | http://localhost:8084/swagger-ui.html |
| Inventory | http://localhost:8085/swagger-ui.html |

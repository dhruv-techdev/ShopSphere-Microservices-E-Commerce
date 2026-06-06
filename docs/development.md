# Development Guide

## Project Structure

```
ShopSphere-Microservices-E-Commerce/
├── docs/                        ← you are here
├── README.md                    ← project overview + frontend snippets
└── shopsphere/                  ← all Maven modules (run commands from here)
    ├── pom.xml                  ← parent POM with dependency management
    ├── docker-compose.yml       ← infra: Postgres, Redis, Kafka, Zookeeper
    ├── common-lib/              ← shared DTOs and Kafka event types
    ├── service-registry/        ← Eureka server
    ├── config-service/          ← Spring Cloud Config Server
    ├── api-gateway/             ← Spring Cloud Gateway + JWT filter
    ├── user-service/            ← registration, login, JWT
    ├── product-service/         ← catalog, categories, search
    ├── cart-service/            ← Redis-backed cart
    ├── order-service/           ← order lifecycle, Kafka producer
    ├── inventory-service/       ← stock management, Kafka consumer
    ├── payment-service/         ← payment simulation, Kafka consumer/producer
    └── notification-service/    ← notification simulation, Kafka consumer
```

---

## Kafka Event Flow

```
order-service  →  [order.placed]     →  inventory-service  (deduct stock)
                                     →  payment-service     (process payment)

payment-service →  [payment.success] →  order-service       (update status)
                →  [payment.failed]  →  order-service       (update status)
                                     →  inventory-service   (restore stock)

order-service   →  [order.confirmed] →  notification-service
inventory-service → [low.stock.alert] → notification-service
```

All events are in `common-lib` under `com.shopsphere.common.events`.

---

## Database Schemas

Each service owns its own PostgreSQL schema in the shared `shopsphere` database.

| Service | Schema |
|---|---|
| user-service | `shopsphere_users` |
| product-service | `public` |
| order-service | `shopsphere_orders` |
| inventory-service | `shopsphere_inventory` |
| notification-service | `shopsphere_notifications` |
| payment-service | `shopsphere_payments` |

Schemas are created automatically by Hikari's `connection-init-sql` on first startup. Tables are managed by Hibernate `ddl-auto: update`.

> **Note:** `shopsphere_notifications` is created lazily on first notification-service startup. If querying it before starting the service, create it manually:
> ```sql
> CREATE SCHEMA IF NOT EXISTS shopsphere_notifications;
> ```

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

---

## Killing a Stuck Port

If a service fails to start because a port is in use:

```bash
kill $(lsof -nP -iTCP:<port> -sTCP:LISTEN | awk 'NR>1{print $2}')
```

Common ports: gateway `8080`, product `8081`, user `8082`, cart `8083`, order `8084`, inventory `8085`, payment `8086`, notification `8087`, Eureka `8761`, config `8888`.

---

## Checking Eureka Registrations

```bash
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" \
  | python3 -c "
import json, sys
apps = json.load(sys.stdin)['applications']['application']
for a in apps:
    print(a['name'], a['instance'][0]['port']['$'])
"
```

---

## Checking Config Server

Each service's resolved config:

```bash
curl -s http://localhost:8888/product-service/default | python3 -m json.tool
curl -s http://localhost:8888/api-gateway/default     | python3 -m json.tool
```

---

## Running a Single Service Build

```bash
cd shopsphere
mvn -pl inventory-service -am clean compile -DskipTests
```

The `-am` flag also builds `common-lib` (a dependency). Add `-DskipTests` to skip tests.

---

## IDE Setup (VS Code)

1. Install the **Extension Pack for Java** (Microsoft).
2. Open `shopsphere/` as the workspace root (where `pom.xml` lives).
3. After opening, run `mvn clean compile -DskipTests` once to generate compiled classes.
4. If you see unresolved import errors, run `Java: Clean Java Language Server Workspace` from the Command Palette (`Cmd+Shift+P`) and restart.

---

## Adding a New Service

1. Create `shopsphere/<new-service>/` with a `pom.xml` that inherits from the parent.
2. Add `<module>new-service</module>` to `shopsphere/pom.xml`.
3. Add the service to `shopsphere/docker-compose.yml` for container builds.
4. Add a route in `api-gateway/src/main/resources/application.yml`.
5. Add the health endpoint to `app.security.public-paths` in `config-service/src/main/resources/config/api-gateway.yml`.

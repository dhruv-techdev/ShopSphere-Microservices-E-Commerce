# ShopSphere — Production-Ready E-Commerce Microservices Backend

A fully functional, production-grade e-commerce backend built on Java microservices. Drop it behind any frontend — React, Next.js, Vue, Angular, Flutter, React Native, or plain HTTP — and ship a complete store without writing a single line of backend code.

Everything routes through a single API Gateway on port `8080`. Services auto-register via Eureka, JWT secures protected routes, Kafka decouples async workflows, and Redis keeps cart operations fast.

---

## Why Use This as Your Backend

- **Single entry point** — all frontend traffic hits `localhost:8080` (or your domain). No per-service URLs to juggle.
- **JWT auth out of the box** — register, login, get a token, attach it to every request.
- **Universal REST + JSON** — any HTTP client on any platform connects the same way.
- **Async event pipeline** — orders trigger inventory deductions and notifications automatically via Kafka.
- **OpenAPI specs on every service** — import into Postman, generate a typed SDK, or feed into any codegen tool.
- **Schema-isolated database** — each service owns its own PostgreSQL schema. Safe to scale or replace independently.
- **Docker-ready** — every service has a production Dockerfile. One command to containerize the whole stack.

---

## Architecture

```
Frontend (any stack)
        │
        ▼
  API Gateway :8080          ← single entry point for all clients
        │
   ┌────┴──────────────────────────────────┐
   │               Services                │
   ├── user-service         :8082          │  JWT auth, registration
   ├── product-service      :8081          │  catalog, search, categories
   ├── cart-service         :8083          │  Redis-backed cart
   ├── order-service        :8084          │  order lifecycle
   ├── inventory-service    :8085          │  stock tracking
   ├── payment-service      :8086          │  payment simulation
   └── notification-service :8087          │  notification simulation
        │
   ┌────┴──────────────────────────────────┐
   │             Infrastructure            │
   ├── PostgreSQL   :5432                  │  persistent data (schema-per-service)
   ├── Redis        :6379                  │  cart sessions
   ├── Kafka        :9092                  │  async events
   ├── Eureka       :8761                  │  service discovery
   └── Config Server :8888                 │  centralized configuration
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot 3.3.5 |
| Service Discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway |
| Centralized Config | Spring Cloud Config Server |
| Databases | PostgreSQL 16 · Redis 7 |
| Messaging | Apache Kafka |
| Auth | JWT (HMAC-SHA256) |
| Build | Maven 3.9+ multi-module |
| Containers | Docker + Docker Compose |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |

---

## Services & Ports

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | **8080** | Single entry point for all clients |
| product-service | 8081 | Catalog, search, categories |
| user-service | 8082 | Registration, login, JWT |
| cart-service | 8083 | Cart CRUD (Redis-backed) |
| order-service | 8084 | Place & view orders |
| inventory-service | 8085 | Stock levels, availability checks |
| payment-service | 8086 | Payment simulation |
| notification-service | 8087 | Notification simulation |
| service-registry | 8761 | Eureka dashboard |
| config-service | 8888 | Centralized Spring Cloud Config |

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Start infrastructure

```bash
cd shopsphere
docker compose up -d
```

Spins up PostgreSQL, Redis, Kafka, and Zookeeper.

### 2. Build all modules

```bash
cd shopsphere
mvn clean install -DskipTests
```

### 3. Start services

Run each in a separate terminal from `shopsphere/`. **Order matters** — start registry and config first.

```bash
mvn -pl service-registry     spring-boot:run   # Eureka       :8761
mvn -pl config-service       spring-boot:run   # Config       :8888
mvn -pl api-gateway          spring-boot:run   # Gateway      :8080
mvn -pl user-service         spring-boot:run   # Auth         :8082
mvn -pl product-service      spring-boot:run   # Catalog      :8081
mvn -pl cart-service         spring-boot:run   # Cart         :8083
mvn -pl order-service        spring-boot:run   # Orders       :8084
mvn -pl inventory-service    spring-boot:run   # Stock        :8085
mvn -pl payment-service      spring-boot:run   # Payments     :8086
mvn -pl notification-service spring-boot:run   # Notifications :8087
```

All frontend traffic goes to **`http://localhost:8080`**.

---

## Connecting a Frontend

All requests go to the **API Gateway at port 8080**. Auth uses standard `Authorization: Bearer <token>` headers. Cart and order endpoints additionally require an `X-User-Id` header (the numeric user ID returned at login).

### Auth Flow (same for every stack)

```
1. POST /api/v1/auth/register   → { token, userId, role }
2. POST /api/v1/auth/login      → { token, userId, role }
3. Store token + userId in your app state / secure storage
4. Every protected request:
     Authorization: Bearer <token>
     X-User-Id: <userId>        ← required for cart & order endpoints
```

---

### React / Vite

```js
// src/api.js
const BASE = 'http://localhost:8080';

export async function login(email, password) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  localStorage.setItem('token', data.token);
  localStorage.setItem('userId', String(data.userId));
  return data;
}

export function authHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('token')}`,
    'X-User-Id': localStorage.getItem('userId') ?? '',
  };
}

export const getProducts = () =>
  fetch(`${BASE}/api/v1/products`).then(r => r.json());

export const placeOrder = body =>
  fetch(`${BASE}/api/v1/orders`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  }).then(r => r.json());
```

---

### Next.js (App Router)

```ts
// lib/api.ts
const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export async function login(email: string, password: string) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
    cache: 'no-store',
  });
  return res.json(); // { token, userId, role }
}

// Server Component — products are public, no token needed
export async function fetchProducts(query = '') {
  const res = await fetch(`${BASE}/api/v1/products?${query}`, {
    next: { revalidate: 60 },
  });
  return res.json();
}

export function authHeaders(token: string, userId: number) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-User-Id': String(userId),
  };
}
```

Set `NEXT_PUBLIC_API_URL=http://localhost:8080` in `.env.local`.

---

### Vue 3 + Pinia

```ts
// stores/auth.ts
import { defineStore } from 'pinia'
import axios from 'axios'

axios.defaults.baseURL = 'http://localhost:8080'

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: '', userId: 0 }),
  actions: {
    async login(email: string, password: string) {
      const { data } = await axios.post('/api/v1/auth/login', { email, password })
      this.token = data.token
      this.userId = data.userId
      axios.defaults.headers.common['Authorization'] = `Bearer ${data.token}`
      axios.defaults.headers.common['X-User-Id'] = String(data.userId)
    },
  },
})
```

---

### Angular

```ts
// core/api.service.ts
import { Injectable } from '@angular/core'
import { HttpClient, HttpHeaders } from '@angular/common/http'

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = 'http://localhost:8080'

  constructor(private http: HttpClient) {}

  login(email: string, password: string) {
    return this.http.post<{ token: string; userId: number }>(
      `${this.base}/api/v1/auth/login`, { email, password }
    )
  }

  private headers(token: string, userId: number) {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'X-User-Id': String(userId),
    })
  }

  getProducts() {
    return this.http.get(`${this.base}/api/v1/products`)
  }

  placeOrder(token: string, userId: number, body: unknown) {
    return this.http.post(`${this.base}/api/v1/orders`, body, {
      headers: this.headers(token, userId),
    })
  }
}
```

Add `provideHttpClient()` in `app.config.ts`.

---

### Svelte / SvelteKit

```ts
// src/lib/api.ts
const BASE = 'http://localhost:8080';

let token = '';
let userId = 0;

export async function login(email: string, password: string) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  token = data.token;
  userId = data.userId;
  return data;
}

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${token}`,
  'X-User-Id': String(userId),
});

export const api = {
  get: (path: string) =>
    fetch(`${BASE}${path}`, { headers: authHeaders() }).then(r => r.json()),
  post: (path: string, body: unknown) =>
    fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(body),
    }).then(r => r.json()),
};
```

---

### React Native

```ts
// services/api.ts
import AsyncStorage from '@react-native-async-storage/async-storage';

// Android emulator: 10.0.2.2 maps to host localhost
// iOS simulator: use localhost directly
const BASE = 'http://10.0.2.2:8080';

export async function login(email: string, password: string) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  await AsyncStorage.multiSet([
    ['token', data.token],
    ['userId', String(data.userId)],
  ]);
  return data;
}

export async function authHeaders() {
  const [[, token], [, userId]] = await AsyncStorage.multiGet(['token', 'userId']);
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-User-Id': userId ?? '',
  };
}
```

---

### Flutter

```dart
// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  // Android emulator: use 10.0.2.2; iOS simulator: use localhost
  static const _base = 'http://10.0.2.2:8080';

  String? _token;
  int? _userId;

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await http.post(
      Uri.parse('$_base/api/v1/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    _token = data['token'] as String;
    _userId = data['userId'] as int;
    return data;
  }

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer $_token',
    if (_userId != null) 'X-User-Id': '$_userId',
  };

  Future<List<dynamic>> getProducts() async {
    final res = await http.get(Uri.parse('$_base/api/v1/products'), headers: _headers);
    return jsonDecode(res.body) as List;
  }

  Future<Map<String, dynamic>> placeOrder(Map<String, dynamic> body) async {
    final res = await http.post(
      Uri.parse('$_base/api/v1/orders'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return jsonDecode(res.body) as Map<String, dynamic>;
  }
}
```

---

## API Reference

### Auth — public, no token required

```
POST /api/v1/auth/register    { email, password, firstName, lastName }
POST /api/v1/auth/login       { email, password }  →  { token, userId, role }
```

### Users

```
GET  /api/v1/users/me         Authorization: Bearer <token>
GET  /api/v1/users/health     (public)
```

### Products — GET is public, write requires JWT + ADMIN role

```
GET    /api/v1/products
GET    /api/v1/products?name=&categoryId=&minPrice=&maxPrice=&inStock=&page=&size=&sort=
GET    /api/v1/products/{id}
POST   /api/v1/products        Authorization: Bearer <token>
PUT    /api/v1/products/{id}   Authorization: Bearer <token>
DELETE /api/v1/products/{id}   Authorization: Bearer <token>
```

### Categories — GET is public

```
GET  /api/v1/categories
POST /api/v1/categories        Authorization: Bearer <token>
```

### Cart — requires Authorization + X-User-Id

```
GET    /api/v1/carts
POST   /api/v1/carts/items              { productId, quantity }
PUT    /api/v1/carts/items/{productId}  { quantity }
DELETE /api/v1/carts/items/{productId}
DELETE /api/v1/carts/clear
```

### Orders — requires Authorization + X-User-Id

```
POST /api/v1/orders            { shippingAddress, ... }
GET  /api/v1/orders/my-orders
GET  /api/v1/orders/{orderId}
```

### Inventory

```
POST /api/v1/inventory                       { productId, quantity }
GET  /api/v1/inventory/{productId}
GET  /api/v1/inventory/low-stock
POST /api/v1/inventory/check-availability    [{ productId, quantity }, ...]
PUT  /api/v1/inventory/{productId}           { quantity }
```

---

## API Docs (Swagger UI)

Every service ships interactive docs at `/swagger-ui.html` and a raw spec at `/v3/api-docs`.

| Service | Swagger UI |
|---|---|
| Product | http://localhost:8081/swagger-ui.html |
| User | http://localhost:8082/swagger-ui.html |
| Cart | http://localhost:8083/swagger-ui.html |
| Order | http://localhost:8084/swagger-ui.html |
| Inventory | http://localhost:8085/swagger-ui.html |

To authenticate: call `POST /api/v1/auth/login`, copy the token, click **Authorize** (top right), paste the token (no `Bearer` prefix).

Raw OpenAPI specs are importable into Postman, Insomnia, or any codegen tool.

---

## Docker Deployment

Every service has a `Dockerfile`. Build and run the full stack with Docker Compose.

```bash
cd shopsphere
docker compose up --build
```

Each service image is built with a two-stage Maven + JRE build. The parent `pom.xml` module references are stripped inside the build container so each service compiles independently.

**Key environment variables per service:**

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/shopsphere
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
SPRING_REDIS_HOST=<redis-host>
KAFKA_BOOTSTRAP=<kafka-host>:9092
EUREKA_URL=http://<eureka-host>:8761/eureka/
CONFIG_SERVER_URL=http://<config-host>:8888
JWT_SECRET=<minimum-256-bit-random-string>
```

---

## Deploying to Production

The backend is environment-agnostic — swap `localhost:8080` for your deployed gateway domain and point any frontend at it.

| Platform | Notes |
|---|---|
| **AWS ECS / Fargate** | One task per service; RDS (Postgres), ElastiCache (Redis), MSK (Kafka) |
| **Kubernetes (GKE / EKS / AKS)** | One Deployment per service; Eureka optional with k8s DNS |
| **Railway / Render** | Push Docker images; set env vars for all connection strings |
| **DigitalOcean App Platform** | Multi-container app with managed Postgres and Redis add-ons |

Configure CORS on the API Gateway to allow your frontend's domain before going live.

---

## Database Schemas

Each service owns its own schema inside the shared `shopsphere` database.

| Service | Schema |
|---|---|
| user-service | `shopsphere_users` |
| product-service | `public` |
| order-service | `shopsphere_orders` |
| inventory-service | `shopsphere_inventory` |
| notification-service | `shopsphere_notifications` |
| payment-service | `shopsphere_payments` |

Schemas are created automatically on first service startup. Tables are managed by Hibernate `ddl-auto: update`.

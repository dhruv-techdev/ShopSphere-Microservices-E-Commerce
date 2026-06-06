# ShopSphere — Production-Ready E-Commerce Microservices Backend

A fully functional, production-grade e-commerce backend built on Java microservices. Drop it behind any frontend — React, Next.js, Vue, Angular, Flutter, React Native, or plain HTTP — and ship a complete store without writing a single line of backend code.

Everything routes through a single API Gateway (`localhost:8080`). Services auto-register, JWT secures protected routes, Kafka decouples async workflows, and Redis keeps cart operations fast.

---

## Why Use This as Your Backend

- **Single entry point** — all frontend traffic hits `localhost:8080` (or your domain). No per-service URLs to juggle.
- **JWT auth out of the box** — register, login, get a token, attach it to every request.
- **Universal REST + JSON** — any HTTP client on any platform connects the same way.
- **Async event pipeline** — orders trigger inventory deductions and notifications automatically via Kafka.
- **OpenAPI specs on every service** — import into Postman, generate a typed SDK, or use with any codegen tool.
- **Schema-isolated database** — each service owns its PostgreSQL schema. Safe to scale independently.

---

## Architecture

```
Frontend (any stack)
        │
        ▼
  API Gateway :8080          ← single entry point
        │
   ┌────┴─────────────────────────────┐
   │             Services             │
   ├── user-service      :8082        │  JWT auth, registration
   ├── product-service   :8081        │  catalog, search, categories
   ├── cart-service      :8083        │  Redis-backed cart
   ├── order-service     :8084        │  order lifecycle
   ├── inventory-service :8085        │  stock tracking
   ├── payment-service   :8086        │  payment simulation
   └── notification-service :8087     │  email/SMS simulation
        │
   ┌────┴─────────────────────────────┐
   │           Infrastructure         │
   ├── PostgreSQL :5432               │  persistent data
   ├── Redis :6379                    │  cart sessions
   ├── Kafka + Zookeeper :9092        │  async events
   └── Eureka :8761                   │  service discovery
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot 3.3.5 |
| Service Discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway |
| Databases | PostgreSQL 16 · Redis 7 |
| Messaging | Apache Kafka |
| Auth | JWT (RS256 / HS256) |
| Build | Maven 3.9+ multi-module |
| Containers | Docker Compose |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |

---

## Services & Ports

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | **8080** | Single entry point for all clients |
| product-service | 8081 | Catalog, search, categories |
| user-service | 8082 | Registration, login, JWT |
| cart-service | 8083 | Cart CRUD (Redis) |
| order-service | 8084 | Place & view orders |
| inventory-service | 8085 | Stock levels, availability |
| payment-service | 8086 | Payment simulation |
| notification-service | 8087 | Notification simulation |
| service-registry | 8761 | Eureka dashboard |
| config-service | 8888 | Centralized config |

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

### 2. Build

```bash
mvn clean install -DskipTests
```

### 3. Start services (each in a separate terminal, from `shopsphere/`)

```bash
mvn -pl service-registry    spring-boot:run
mvn -pl api-gateway         spring-boot:run
mvn -pl user-service        spring-boot:run
mvn -pl product-service     spring-boot:run
mvn -pl cart-service        spring-boot:run
mvn -pl order-service       spring-boot:run
mvn -pl inventory-service   spring-boot:run
mvn -pl payment-service     spring-boot:run
mvn -pl notification-service spring-boot:run
```

All client traffic goes to `http://localhost:8080`.

---

## Connecting a Frontend

All requests go to the **API Gateway at port 8080**. Auth uses standard `Authorization: Bearer <token>` headers. Cart and order endpoints additionally require an `X-User-Id` header (the user's numeric ID from the login response).

### Auth Flow (all stacks)

```
1. POST /api/v1/auth/register   → creates account
2. POST /api/v1/auth/login      → returns { token, userId, ... }
3. Store token + userId
4. Every subsequent request:
     Authorization: Bearer <token>
     X-User-Id: <userId>        (cart & order endpoints only)
```

---

### React / React + Vite

```js
// api.js
const BASE = 'http://localhost:8080';

export async function login(email, password) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  localStorage.setItem('token', data.token);
  localStorage.setItem('userId', data.userId);
  return data;
}

export function authHeaders(userId) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('token')}`,
    ...(userId && { 'X-User-Id': String(userId) }),
  };
}

// Fetch products
export const getProducts = () =>
  fetch(`${BASE}/api/v1/products`).then(r => r.json());

// Place an order
export const placeOrder = (userId, body) =>
  fetch(`${BASE}/api/v1/orders`, {
    method: 'POST',
    headers: authHeaders(userId),
    body: JSON.stringify(body),
  }).then(r => r.json());
```

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
  return res.json(); // { token, userId }
}

// Server Component — products are public, no token needed
export async function fetchProducts(params?: string) {
  const res = await fetch(`${BASE}/api/v1/products?${params ?? ''}`, {
    next: { revalidate: 60 },
  });
  return res.json();
}

// Client Component helper
export function buildHeaders(token: string, userId?: number) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    ...(userId !== undefined && { 'X-User-Id': String(userId) }),
  };
}
```

Set `NEXT_PUBLIC_API_URL=http://localhost:8080` in `.env.local`.

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
    },
  },
})

// Composable for protected calls
export function useApi() {
  const auth = useAuthStore()
  const headers = computed(() => ({
    Authorization: `Bearer ${auth.token}`,
    'X-User-Id': String(auth.userId),
  }))
  return { headers }
}
```

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

  private headers(token: string, userId?: number) {
    let h = new HttpHeaders({ Authorization: `Bearer ${token}` })
    if (userId) h = h.set('X-User-Id', String(userId))
    return h
  }

  getProducts() {
    return this.http.get(`${this.base}/api/v1/products`)
  }

  placeOrder(token: string, userId: number, body: unknown) {
    return this.http.post(
      `${this.base}/api/v1/orders`, body,
      { headers: this.headers(token, userId) }
    )
  }
}
```

Add `provideHttpClient()` in `app.config.ts`.

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

export const api = {
  get: (path: string) =>
    fetch(`${BASE}${path}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-User-Id': String(userId) },
    }).then(r => r.json()),

  post: (path: string, body: unknown) =>
    fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'X-User-Id': String(userId),
      },
      body: JSON.stringify(body),
    }).then(r => r.json()),
};
```

### React Native

```ts
// services/api.ts
const BASE = 'http://10.0.2.2:8080'; // Android emulator → localhost
// Use 'http://localhost:8080' for iOS simulator

import AsyncStorage from '@react-native-async-storage/async-storage';

export async function login(email: string, password: string) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  await AsyncStorage.multiSet([['token', data.token], ['userId', String(data.userId)]]);
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

### Flutter

```dart
// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  static const base = 'http://10.0.2.2:8080'; // Android emulator
  // static const base = 'http://localhost:8080'; // iOS simulator

  String? _token;
  int? _userId;

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await http.post(
      Uri.parse('$base/api/v1/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    final data = jsonDecode(res.body);
    _token = data['token'];
    _userId = data['userId'];
    return data;
  }

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer $_token',
    if (_userId != null) 'X-User-Id': _userId.toString(),
  };

  Future<List<dynamic>> getProducts() async {
    final res = await http.get(Uri.parse('$base/api/v1/products'), headers: _headers);
    return jsonDecode(res.body);
  }

  Future<Map<String, dynamic>> placeOrder(Map<String, dynamic> body) async {
    final res = await http.post(
      Uri.parse('$base/api/v1/orders'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return jsonDecode(res.body);
  }
}
```

---

## API Reference

### Auth (public — no token required)

```
POST /api/v1/auth/register    { email, password, firstName, lastName }
POST /api/v1/auth/login       { email, password }  →  { token, userId, role }
```

### Users

```
GET  /api/v1/users/me         Authorization: Bearer <token>
GET  /api/v1/users/health     (public)
```

### Products

```
GET    /api/v1/products                                    (public)
GET    /api/v1/products?name=&categoryId=&minPrice=&maxPrice=&inStock=&page=&size=&sort=
GET    /api/v1/products/{id}                               (public)
POST   /api/v1/products        Authorization: Bearer <token>
PUT    /api/v1/products/{id}   Authorization: Bearer <token>
DELETE /api/v1/products/{id}   Authorization: Bearer <token>
```

### Categories

```
GET  /api/v1/categories        (public)
POST /api/v1/categories        Authorization: Bearer <token>
```

### Cart (requires Authorization + X-User-Id)

```
GET    /api/v1/carts
POST   /api/v1/carts/items            { productId, quantity }
PUT    /api/v1/carts/items/{productId} { quantity }
DELETE /api/v1/carts/items/{productId}
DELETE /api/v1/carts/clear
```

### Orders (requires Authorization + X-User-Id)

```
POST /api/v1/orders            { shippingAddress, ... }
GET  /api/v1/orders/my-orders
GET  /api/v1/orders/{orderId}
```

### Inventory

```
POST /api/v1/inventory                  { productId, quantity }
GET  /api/v1/inventory/{productId}
GET  /api/v1/inventory/low-stock
POST /api/v1/inventory/check-availability  [{ productId, quantity }, ...]
PUT  /api/v1/inventory/{productId}      { quantity }
```

---

## API Docs (Swagger UI)

Every service ships with interactive docs. Authenticate once in the User Service, copy the token, and test any endpoint.

| Service | Swagger UI |
|---|---|
| Product | http://localhost:8081/swagger-ui.html |
| User | http://localhost:8082/swagger-ui.html |
| Cart | http://localhost:8083/swagger-ui.html |
| Order | http://localhost:8084/swagger-ui.html |
| Inventory | http://localhost:8085/swagger-ui.html |

Raw OpenAPI specs at `/v3/api-docs` on each service — importable into Postman, Insomnia, or any codegen tool.

---

## Deploying to Production

The backend is environment-agnostic. Point any frontend to the deployed gateway URL and swap `localhost:8080` for your domain.

**Recommended deploy targets:**

| Target | Notes |
|---|---|
| AWS ECS / Fargate | Containerize each service; use RDS for Postgres, ElastiCache for Redis, MSK for Kafka |
| Kubernetes (GKE / EKS / AKS) | One Deployment per service; Eureka optional when using k8s DNS |
| Railway / Render | Push Docker images; set env vars for DB/Kafka connection strings |
| DigitalOcean App Platform | Multi-container apps with managed Postgres and Redis add-ons |

**Environment variables to set per service:**

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/shopsphere
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
SPRING_REDIS_HOST=<redis-host>
KAFKA_BOOTSTRAP=<kafka-host>:9092
```

**CORS** — configure allowed origins on the API Gateway for your frontend domain before going live.

---

## Database Schemas

| Service | Schema |
|---|---|
| user-service | `shopsphere_users` |
| product-service | `public` |
| order-service | `shopsphere_orders` |
| inventory-service | `shopsphere_inventory` |
| notification-service | `shopsphere_notifications` |
| payment-service | `shopsphere_payments` |

Schemas are created automatically on service startup. Tables are managed by Hibernate `ddl-auto: update`.

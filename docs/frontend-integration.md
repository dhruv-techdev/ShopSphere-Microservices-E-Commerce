# Frontend Integration

All requests target the **API Gateway at `http://localhost:8080`** (or your deployed domain in production). The gateway handles routing, JWT validation, and service discovery — your frontend never talks to individual services directly.

---

## Auth Pattern

Every stack follows the same pattern:

1. Call `POST /api/v1/auth/login` → get `{ token, userId, role }`
2. Store `token` and `userId` (localStorage, Pinia store, Riverpod, etc.)
3. Attach `Authorization: Bearer <token>` to every protected request
4. Attach `X-User-Id: <userId>` for cart and order endpoints

---

## React / Vite

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

// Public — no auth needed
export const getProducts = (query = '') =>
  fetch(`${BASE}/api/v1/products?${query}`).then(r => r.json());

// Protected
export const getCart = () =>
  fetch(`${BASE}/api/v1/carts`, { headers: authHeaders() }).then(r => r.json());

export const addToCart = (productId, quantity) =>
  fetch(`${BASE}/api/v1/carts/items`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ productId, quantity }),
  }).then(r => r.json());

export const placeOrder = (shippingAddress) =>
  fetch(`${BASE}/api/v1/orders`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ shippingAddress }),
  }).then(r => r.json());
```

Set `VITE_API_URL=http://localhost:8080` in `.env` and use `import.meta.env.VITE_API_URL` instead of the hardcoded string.

---

## Next.js (App Router)

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
  return res.json() as Promise<{ token: string; userId: number; role: string }>;
}

// Server Component — public, ISR-cached
export async function fetchProducts(query = '') {
  const res = await fetch(`${BASE}/api/v1/products?${query}`, {
    next: { revalidate: 60 },
  });
  return res.json();
}

// Client helper
export function authHeaders(token: string, userId: number) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-User-Id': String(userId),
  };
}
```

`.env.local`:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

For Server Actions that need auth, store the token in an `httpOnly` cookie and read it server-side.

---

## Vue 3 + Pinia

```ts
// stores/auth.ts
import { defineStore } from 'pinia'
import axios from 'axios'

axios.defaults.baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: '', userId: 0, role: '' }),
  actions: {
    async login(email: string, password: string) {
      const { data } = await axios.post('/api/v1/auth/login', { email, password })
      this.token = data.token
      this.userId = data.userId
      this.role = data.role
      axios.defaults.headers.common['Authorization'] = `Bearer ${data.token}`
      axios.defaults.headers.common['X-User-Id'] = String(data.userId)
    },
    logout() {
      this.token = ''
      this.userId = 0
      delete axios.defaults.headers.common['Authorization']
      delete axios.defaults.headers.common['X-User-Id']
    },
  },
  persist: true, // pinia-plugin-persistedstate
})
```

```ts
// composables/useProducts.ts
import axios from 'axios'

export function useProducts() {
  const fetch = (query = '') => axios.get(`/api/v1/products?${query}`)
  return { fetch }
}
```

---

## Angular

```ts
// core/api.service.ts
import { Injectable } from '@angular/core'
import { HttpClient, HttpHeaders } from '@angular/common/http'
import { environment } from '../environments/environment'

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiUrl  // set to 'http://localhost:8080'

  constructor(private http: HttpClient) {}

  login(email: string, password: string) {
    return this.http.post<{ token: string; userId: number; role: string }>(
      `${this.base}/api/v1/auth/login`,
      { email, password }
    )
  }

  private headers(token: string, userId: number) {
    return new HttpHeaders({
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'X-User-Id': String(userId),
    })
  }

  getProducts(query = '') {
    return this.http.get(`${this.base}/api/v1/products?${query}`)
  }

  getCart(token: string, userId: number) {
    return this.http.get(`${this.base}/api/v1/carts`, {
      headers: this.headers(token, userId),
    })
  }

  placeOrder(token: string, userId: number, shippingAddress: string) {
    return this.http.post(
      `${this.base}/api/v1/orders`,
      { shippingAddress },
      { headers: this.headers(token, userId) }
    )
  }
}
```

`app.config.ts`:
```ts
import { provideHttpClient } from '@angular/common/http'
export const appConfig = { providers: [provideHttpClient()] }
```

---

## Svelte / SvelteKit

```ts
// src/lib/api.ts
const BASE = import.meta.env.PUBLIC_API_URL ?? 'http://localhost:8080';

let _token = '';
let _userId = 0;

export async function login(email: string, password: string) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  _token = data.token;
  _userId = data.userId;
  return data;
}

const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${_token}`,
  'X-User-Id': String(_userId),
});

export const api = {
  get:  (path: string)              => fetch(`${BASE}${path}`, { headers: headers() }).then(r => r.json()),
  post: (path: string, body: unknown) => fetch(`${BASE}${path}`, {
    method: 'POST', headers: headers(), body: JSON.stringify(body),
  }).then(r => r.json()),
  del:  (path: string)              => fetch(`${BASE}${path}`, { method: 'DELETE', headers: headers() }),
};
```

For SvelteKit server-side requests (in `+page.server.ts`), forward the token via cookies or `locals`.

---

## React Native

```ts
// services/api.ts
import AsyncStorage from '@react-native-async-storage/async-storage';

// Android emulator → host machine localhost
// iOS simulator → use 'http://localhost:8080'
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

async function authHeaders() {
  const [[, token], [, userId]] = await AsyncStorage.multiGet(['token', 'userId']);
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token ?? ''}`,
    'X-User-Id': userId ?? '0',
  };
}

export async function getCart() {
  return fetch(`${BASE}/api/v1/carts`, { headers: await authHeaders() }).then(r => r.json());
}

export async function placeOrder(shippingAddress: string) {
  return fetch(`${BASE}/api/v1/orders`, {
    method: 'POST',
    headers: await authHeaders(),
    body: JSON.stringify({ shippingAddress }),
  }).then(r => r.json());
}
```

---

## Flutter

```dart
// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  // Android emulator: 10.0.2.2 → host localhost
  // iOS simulator: localhost
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
    'Authorization': 'Bearer ${_token ?? ''}',
    if (_userId != null) 'X-User-Id': '$_userId',
  };

  Future<List<dynamic>> getProducts({String query = ''}) async {
    final res = await http.get(
      Uri.parse('$_base/api/v1/products?$query'),
      headers: _headers,
    );
    return jsonDecode(res.body) as List;
  }

  Future<Map<String, dynamic>> getCart() async {
    final res = await http.get(Uri.parse('$_base/api/v1/carts'), headers: _headers);
    return jsonDecode(res.body) as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> placeOrder(String shippingAddress) async {
    final res = await http.post(
      Uri.parse('$_base/api/v1/orders'),
      headers: _headers,
      body: jsonEncode({'shippingAddress': shippingAddress}),
    );
    return jsonDecode(res.body) as Map<String, dynamic>;
  }
}
```

Add to `pubspec.yaml`:
```yaml
dependencies:
  http: ^1.2.0
```

---

## CORS

In development, the API Gateway allows all origins. Before deploying to production, configure allowed origins in the gateway's `application.yml`:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "https://your-frontend-domain.com"
            allowedMethods: ["GET","POST","PUT","DELETE","OPTIONS"]
            allowedHeaders: ["*"]
            allowCredentials: true
```

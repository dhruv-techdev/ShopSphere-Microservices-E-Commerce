# Authentication

ShopSphere uses **JWT (HMAC-SHA256)**. The API Gateway validates every token before forwarding requests downstream. Public endpoints pass through without a token.

---

## Auth Flow

```
1. POST /api/v1/auth/register  →  { token, userId, role }
2. POST /api/v1/auth/login     →  { token, userId, role }
3. Store token + userId in your app
4. Attach to every protected request:
     Authorization: Bearer <token>
     X-User-Id: <userId>          ← required for cart & order endpoints
```

---

## Register

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "hunter2",
    "firstName": "Jane",
    "lastName": "Doe"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "role": "USER"
}
```

---

## Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"hunter2"}'
```

Same response shape as register.

---

## Using the Token

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
USER_ID=1

# Profile
curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"

# Cart (requires X-User-Id)
curl -s http://localhost:8080/api/v1/carts \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID"

# Place order (requires X-User-Id)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" \
  -d '{"shippingAddress":"123 Main St"}'
```

---

## Public vs Protected Endpoints

| Endpoint | Auth Required |
|---|---|
| `POST /api/v1/auth/register` | No |
| `POST /api/v1/auth/login` | No |
| `GET /api/v1/products/**` | No |
| `GET /api/v1/categories/**` | No |
| `GET /api/v1/*/health` | No |
| `GET /api/v1/users/me` | Bearer token |
| `POST/PUT/DELETE /api/v1/products/**` | Bearer token + ADMIN role |
| `POST/PUT/DELETE /api/v1/categories/**` | Bearer token + ADMIN role |
| `POST/PUT/DELETE /api/v1/inventory/**` | Bearer token + ADMIN role |
| All cart endpoints | Bearer token + X-User-Id |
| All order endpoints | Bearer token + X-User-Id |

---

## Roles

| Role | Permissions |
|---|---|
| `USER` | Browse catalog, manage own cart, place & view own orders |
| `ADMIN` | Everything USER can do + create/edit/delete products, categories, inventory |

The role is embedded in the JWT and enforced at the gateway.

---

## Authenticating in Swagger UI

1. Open any service's Swagger UI (e.g. http://localhost:8082/swagger-ui.html)
2. Call `POST /api/v1/auth/login` — copy the `token` value
3. Click the **Authorize** button (top right)
4. Paste the token — **no `Bearer` prefix**
5. Click Authorize → Close
6. All protected endpoints in that tab will now include the token automatically

---

## Token Expiry

Default: **24 hours** (`86400000 ms`). Override with the `JWT_EXPIRATION_MS` env var. When a token expires, login again to get a fresh one.

---

## JWT Secret

The default secret is `change-me-to-a-long-random-secret-of-at-least-256-bits-for-hs256`. Override it in production:

```bash
JWT_SECRET=your-256-bit-random-string mvn -pl user-service spring-boot:run
# or set JWT_SECRET env var in your container/deployment
```

The same secret must be set for both `user-service` (issues tokens) and `api-gateway` (validates tokens).

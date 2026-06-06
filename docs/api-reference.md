# API Reference

All requests go through the **API Gateway at `http://localhost:8080`**.

---

## Auth

Public — no token required.

### Register
```
POST /api/v1/auth/register
Content-Type: application/json

{ "email": "string", "password": "string", "firstName": "string", "lastName": "string" }
```
Response: `{ token, userId, role }`

### Login
```
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "string", "password": "string" }
```
Response: `{ token, userId, role }`

---

## Users

### Get current user
```
GET /api/v1/users/me
Authorization: Bearer <token>
```

### Health check (public)
```
GET /api/v1/users/health
```

---

## Products

GET endpoints are public. Write endpoints require a JWT with ADMIN role.

### List / search products
```
GET /api/v1/products
GET /api/v1/products?name=shoe&categoryId=2&minPrice=10&maxPrice=200&inStock=true&page=0&size=20&sort=price,asc
```

### Get product by ID
```
GET /api/v1/products/{id}
```

### Create product
```
POST /api/v1/products
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "name": "string",
  "description": "string",
  "price": 0.0,
  "categoryId": 0,
  "active": true
}
```

### Update product
```
PUT /api/v1/products/{id}
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "name": "string", "price": 0.0, ... }
```

### Delete product
```
DELETE /api/v1/products/{id}
Authorization: Bearer <admin-token>
```

### Health check (public)
```
GET /api/v1/products/health
```

---

## Categories

### List categories (public)
```
GET /api/v1/categories
```

### Create category
```
POST /api/v1/categories
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "name": "string", "description": "string" }
```

---

## Cart

All cart endpoints require `Authorization: Bearer <token>` and `X-User-Id: <userId>`.

### Get cart
```
GET /api/v1/carts
```

### Add item
```
POST /api/v1/carts/items
Content-Type: application/json

{ "productId": 1, "quantity": 2 }
```

### Update item quantity
```
PUT /api/v1/carts/items/{productId}
Content-Type: application/json

{ "quantity": 3 }
```

### Remove item
```
DELETE /api/v1/carts/items/{productId}
```

### Clear cart
```
DELETE /api/v1/carts/clear
```

### Health check (public)
```
GET /api/v1/carts/health
```

---

## Orders

All order endpoints require `Authorization: Bearer <token>` and `X-User-Id: <userId>`.

### Place order
Places an order from the current cart contents. Cart is cleared on success.

```
POST /api/v1/orders
Content-Type: application/json

{ "shippingAddress": "string" }
```

### List my orders
```
GET /api/v1/orders/my-orders
```

### Get order by ID
```
GET /api/v1/orders/{orderId}
```

### Health check (public)
```
GET /api/v1/orders/health
```

---

## Inventory

Write endpoints require ADMIN role.

### Initialize stock for a product
```
POST /api/v1/inventory
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "productId": 1, "quantity": 100 }
```

### Get stock for a product
```
GET /api/v1/inventory/{productId}
```

### Get low-stock items
```
GET /api/v1/inventory/low-stock
```
Returns products where quantity ≤ threshold (default: 10).

### Batch availability check
```
POST /api/v1/inventory/check-availability
Content-Type: application/json

[
  { "productId": 1, "quantity": 2 },
  { "productId": 3, "quantity": 1 }
]
```
Returns availability status for each item.

### Update stock
```
PUT /api/v1/inventory/{productId}
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "quantity": 50, "operation": "SET" }
```
Operations: `SET`, `INCREMENT`, `DECREMENT`

### Health check (public)
```
GET /api/v1/inventory/health
```

---

## Payments

### Health check
```
GET /api/v1/payments/health
```

Payments are triggered automatically by the order service via Kafka — not called directly by clients.

---

## Notifications

### Health check
```
GET /api/v1/notifications/health
```

Notifications are triggered automatically by Kafka events — not called directly by clients.

---

## Response Codes

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 400 | Bad request — check request body |
| 401 | Missing or invalid JWT |
| 403 | Insufficient role (ADMIN required) |
| 404 | Resource not found |
| 409 | Conflict (e.g. product already has inventory entry) |
| 500 | Internal server error |

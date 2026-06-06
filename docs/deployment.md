# Deployment

## Docker (Local Full Stack)

Build and run all services as containers:

```bash
cd shopsphere
docker compose build --parallel
docker compose up
```

Each service has a two-stage Dockerfile:
1. **Build stage** — Maven + JDK 21 compiles the JAR
2. **Runtime stage** — slim JRE 21 Alpine runs it

The parent POM and `common-lib` are installed into the Maven local repo first, then each service compiles against them independently — no inter-service source coupling in the Docker build.

---

## Environment Variables

Set these per-service in your container orchestrator or `.env` file:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/shopsphere` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `shopsphere` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `shopsphere` | DB password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka broker |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka endpoint |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Config Server URL |
| `JWT_SECRET` | *(weak default — must override)* | 256-bit secret shared between user-service and api-gateway |
| `JWT_EXPIRATION_MS` | `86400000` | Token TTL in ms (default 24h) |

**Always override `JWT_SECRET` in production** — use a random 256-bit string.

---

## Startup Order

Services must start in dependency order:

```
1. Infrastructure (Postgres, Redis, Kafka, Zookeeper)
2. service-registry  — Eureka
3. config-service    — Spring Cloud Config
4. api-gateway       — depends on Eureka + Config
5. All other services — depend on Eureka + Config
```

In Docker Compose, use `depends_on` with `condition: service_healthy` to enforce this.

---

## AWS ECS / Fargate

1. Push each service image to ECR:
   ```bash
   aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
   docker tag shopsphere-product-service:latest <account>.dkr.ecr.<region>.amazonaws.com/shopsphere-product-service:latest
   docker push <account>.dkr.ecr.<region>.amazonaws.com/shopsphere-product-service:latest
   ```

2. Create an ECS cluster and define Task Definitions for each service.

3. Managed infrastructure replacements:
   - PostgreSQL → **Amazon RDS** (Postgres 16)
   - Redis → **Amazon ElastiCache** (Redis 7)
   - Kafka → **Amazon MSK**

4. Set environment variables in the Task Definition for each service.

5. Use an **Application Load Balancer** in front of `api-gateway`. All traffic enters through the ALB — never expose individual services publicly.

---

## Kubernetes

1. One `Deployment` + `Service` per microservice.
2. Use a `ConfigMap` for shared config and `Secrets` for credentials.
3. With Kubernetes DNS, services can reach each other by name — Eureka becomes optional. Disable Eureka registration:
   ```yaml
   eureka:
     client:
       register-with-eureka: false
       fetch-registry: false
   ```
   And point service URLs directly: `USER_SERVICE_URL=http://user-service:8082`

4. Expose only the `api-gateway` via an `Ingress`.

Example deployment (one service):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: product-service
  template:
    metadata:
      labels:
        app: product-service
    spec:
      containers:
      - name: product-service
        image: your-registry/shopsphere-product-service:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
```

---

## Railway / Render

1. Connect your GitHub repo.
2. Add a service for each Docker image.
3. Set environment variables in the dashboard.
4. Use Railway/Render managed Postgres and Redis add-ons.
5. The `api-gateway` service gets the public URL — all others stay internal.

---

## Production Checklist

- [ ] `JWT_SECRET` set to a strong random value (same on user-service and api-gateway)
- [ ] CORS configured for your frontend domain
- [ ] Postgres, Redis, Kafka on managed infrastructure (not containers)
- [ ] `spring.jpa.hibernate.ddl-auto` set to `validate` or `none` (not `update`) with proper migrations
- [ ] Health check endpoints wired into load balancer (`/actuator/health`)
- [ ] Only `api-gateway` is publicly accessible — all other services are internal
- [ ] TLS/HTTPS termination at the load balancer

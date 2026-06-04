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

## Run infra
    docker compose up -d

## Build everything
    mvn clean install

## Status
US1 done — Base project structure
US2 next — Product Service

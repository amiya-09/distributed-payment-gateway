# Distributed Payment Gateway

A microservices version of [Payment-Gateway](https://github.com/amiya-09/Payment-Gateway), split into independently deployable Spring Boot services behind an API gateway.

## Services

| Service | Port | Responsibility |
| --- | --- | --- |
| `config-service` | 8888 | Spring Cloud Config server, backed by [distributed-payment-gateway-config](https://github.com/amiya-09/distributed-payment-gateway-config) |
| `discovery-service` | 8761 | Eureka service registry |
| `api-gateway-service` | 8080 | Single entry point; JWT and API-key authentication, routing to services |
| `merchant-service` | 9010 | Merchant signup and login, API keys, customers, webhook configuration |
| `payment-service` | 9020 | Orders, payments, payment state machine, transactional outbox |
| `operations-service` | 9030 | Webhook delivery with retries and dead-lettering, daily settlement |
| `vault-service` | 9040 | Card tokenization and encrypted card storage |
| `common-lib` | – | Shared DTOs, enums, exceptions, idempotency, rate limiting and auto-configuration |

## Tech stack

Java 25 · Spring Boot 4.1 · Spring Cloud 2025.1 (Config, Eureka, Gateway, OpenFeign) · PostgreSQL · Redis · Kafka

## Running locally

1. Start PostgreSQL, Redis and Kafka.
2. Install the shared library: `cd common-lib && ./mvnw clean install`.
3. Create `config-service/src/main/resources/application.yaml` pointing at the config repository (it is git-ignored).
4. Start the services in this order: `config-service` → `discovery-service` → the remaining services.

Re-run step 2 after any change to `common-lib`.

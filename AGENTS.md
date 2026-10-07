# AGENTS.md — EventPay

Fuente de verdad: `docs/PLAN.md`. Este fichero define cómo deben trabajar los agentes en este repo.

## Stack innegociable

Java 17, Spring Boot 3.2.x, Spring Cloud 2023.0.x, Angular 18, Apache Kafka 3.7 (KRaft), Postgres 16.
Kafka es siempre Apache Kafka local (`kafka:9092` en compose, `localhost:29092` en local). Serde JSON, sin Avro/Schema Registry.

## Orden de trabajo (D1 -> D7)

1. D1: infra compose + identity (register/login JWT) + Gateway route `/auth/**`.
2. D2: booking-service hexagonal síncrono (dominio puro + ports + JPA + REST).
3. D3: Kafka (`booking.requested` -> worker simulado -> `payment.result` + DLT).
4. D4: Gateway AuthFilter (JWT -> `X-User-Id`), Eureka, idempotencia, SSE.
5. D5: Angular 18 (auth interceptor, events, bookings, SSE).
6. D6-D7: tests + README demo + tag `v0.1-mvp`.

No paralelizar D2/D3: el flujo async depende del dominio ya estable.

## Reglas globales

- Español en docs y mensajes de commit.
- Gateway `:8080` es la única entrada desde Angular. Nada de llamar directo a `:8081`/`:8082` desde el front.
- Hexagonal estricta solo en booking-service: `domain/` sin `org.springframework.*`.
- Commits pequeños por día (`d1-identity`, `d3-kafka`, ...). No mezclar servicios en un mismo commit salvo compose.
- `docker compose up --build` debe quedar siempre en verde antes de cerrar el día.

## Comandos

- Backend: `mvn verify` (tests: JUnit5 + Mockito + EmbeddedKafka + ArchUnit).
- Infra: `docker compose up --build -d` / `docker compose logs --tail=20`.
- Front: `npm install && ng serve` (puerto 4200 -> Gateway 8080).

## Done por día

Según `docs/PLAN.md` sección 5. Criterio final: demo 60s login -> reservar -> PENDING pasa a PAID en vivo por SSE.

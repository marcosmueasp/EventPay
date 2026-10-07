# EventPay — Plan de desarrollo

> Stack fijado: Java 17, Spring Boot 3.2.x, Spring Cloud 2023.0.x, Angular 18, Kafka 3.7 (KRaft), Postgres 16.
> Tiempo objetivo: 1 semana (~3-4h/día). Idioma del plan: ES.

## 1. Qué se va a desarrollar

**EventPay** es una demo de reservas de eventos con pago asíncrono simulado, pensada para portfolio y para cubrir una oferta que pide: Java 17+, Spring Boot, Spring Cloud, Spring Security, microservicios, REST, JUnit5+Mockito, Kafka y arquitectura hexagonal.

Flujo MVP:

```
Angular -> Gateway :8080 -> identity-service :8081 (registro/login JWT)
                      \-> booking-service :8082 (hexagonal + Kafka)
booking-service guarda Booking PENDING en Postgres -> publica booking.requested
payment-worker (interno a booking) consume booking.requested, espera 2s, publica payment.result (PAID/REJECTED)
booking-service consume payment.result y actualiza a PAID -> Angular lo ve por SSE sin refrescar
```

Lo que NO se hace para que quepa en 1 semana: Config Server, Schema Registry/Avro (JSON), ELK, K8s, OAuth2 externo, saga distribuida completa, micro de payment separado. El worker de pago vive dentro de booking como adapter sustituible — se justifica así en entrevista.

## 2. Mapa oferta -> evidencia

| Requisito oferta | Dónde se demuestra |
|---|---|
| Java 17+ | Todo el backend, `maven.compiler.release=17` |
| Spring Boot | 3 servicios Boot 3.2.x |
| Spring Cloud | Gateway + Eureka + OpenFeign (booking valida usuario contra identity) |
| Spring Security | identity-service BCrypt + JJWT HS256; Gateway valida JWT y propaga `X-User-Id` |
| Microservicios | gateway + identity + booking, cada uno con su Postgres (users-db, booking-db) |
| APIs REST | `POST /auth/register`, `POST /auth/login`, `GET/POST /api/events`, `POST /api/bookings`, `GET /api/bookings/me`, `GET /api/bookings/{id}/stream` (SSE) |
| JUnit5 + Mockito | Tests de `BookingService` con mocks, `@WebMvcTest`, EmbeddedKafka, ArchUnit |
| Kafka + hexagonal | `booking-service` con ports `BookingRepositoryPort/EventPublisherPort`, adapters `KafkaBookingPublisher`, `PaymentResultListener`, retry 3x + DLT |

## 3. Arquitectura y puertos

```
[Angular :4200] -> [Gateway :8080] -> /auth/** -> identity :8081 -> users-db :5433
                                   -> /api/**  -> booking  :8082 -> booking-db :5434
booking :8082 <-> Kafka :9092 (kraft, single-node)
Eureka :8761 (discovery)
```

Topics JSON v1: `booking.requested {bookingId,eventId,userId,requestedAt}` (clave `eventId`), `payment.result {bookingId,status:PAID|REJECTED,reason}`, `user.created {userId,email}`, más `*.DLT` con `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.

DB mínima: `users(id UUID,email unique,pass_hash)`, `events(id UUID,name,capacity,sold)`, `bookings(id UUID,event_id,user_id,status,idempotency_key unique)`.

Seguridad: identity firma JWT 1h (`sub=userId`). Gateway `AuthFilter` valida y reenvía `X-User-Id`. Booking no valida JWT, solo confía en header interno — simple y explicable.

## 4. Estructura hexagonal (solo booking-service)

```
booking-service/src/main/java/com/eventpay/booking/
  domain/model/{Event,Booking,BookingStatus}          // POJO puro, cero Spring
  domain/port/in/{CreateBookingUseCase,ListBookingsUseCase}
  domain/port/out/{BookingRepositoryPort,EventPublisherPort}
  application/service/BookingService                  // implements in-ports
  infrastructure/
    in/web/{BookingController,dto,mapper}            // MapStruct
    in/sse/BookingSseController
    in/kafka/PaymentResultListener                   // @KafkaListener payment.result
    out/persistence/{BookingEntity,JpaBookingRepo,BookingPersistenceAdapter}
    out/kafka/{KafkaBookingPublisher,config/KafkaTopicConfig}
```

Regla ArchUnit: `domain` no importa `org.springframework.*` ni `infrastructure`. Identity-service es layered simple (web/service/jpa) a propósito.

## 5. Plan 7 días

- **D1 Base:** monorepo, `docker-compose.yml` (postgres x2 + kafka + eureka), identity register/login JWT + test Mockito, Gateway route `/auth/**`.
- **D2 Booking sync:** dominio + ports + `BookingService` PENDING + JPA adapter + REST + test use-case sin Spring.
- **D3 Kafka core:** `KafkaTopicConfig`, `KafkaBookingPublisher` en alta, `PaymentSimWorker` (listener `booking.requested` -> sleep 2s -> `payment.result`), `PaymentResultListener`, test EmbeddedKafka.
- **D4 Resiliencia + Cloud:** retry + DLT, header `Idempotency-Key` unique, Gateway Auth filter JWT -> `X-User-Id`, registro Eureka, endpoint SSE.
- **D5 Angular 18:** login (guarda token), interceptor Bearer, páginas events/bookings, SSE PENDING->PAID. Solo 3 componentes standalone.
- **D6 Testing + docs:** ArchUnit, `@WebMvcTest`, README con diagrama + `docker compose up --build` + `.http` + gif demo.
- **D7 Buffer:** demo DLQ con mensaje corrupto, CORS, perfiles `local/docker`, tag `v0.1-mvp`.

Done: `docker compose up` levanta todo en <2min; demo 60s login->reservar->PAID en vivo; 5 tests en verde (2 dominio/mockito, 1 web, 1 kafka, 1 archunit).

## 6. Cómo correr (objetivo)

```bash
docker compose up --build
# Angular: npm install && ng serve (puerto 4200, apunta a Gateway 8080)
```

Orquestación IA: pedir al agente principal `Día X` y él usa el skill de `.opencode/skills/` correspondiente en orden D1->D7.

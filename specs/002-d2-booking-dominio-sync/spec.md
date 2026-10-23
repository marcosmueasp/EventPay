# 002 — D2 Booking dominio síncrono (Día 2)

- Estado: pendiente (bloqueado por 001 solo a nivel de módulos pom; el dominio no depende de identity en código)
- Enlaces: `docs/CONSTITUTION.md §2, §3, §4, §5` · `AGENTS.md` · `MEMORY.md`

## 1. Objetivo

`booking-service` hexagonal **síncrono**: crear y listar reservas en `PENDING` sin Kafka todavía.

## 2. Alcance / No hacer

- Sí: dominio puro + ports + `BookingService` + adapter JPA + REST + módulo en pom/compose.
- No: Kafka (`KafkaBookingPublisher`, listeners, worker, topics), DLT/retry, AuthFilter, SSE, Angular.
  No publicar ni consumir ningún topic en este día.

## 3. Contratos

- `GET /api/events` -> `200 [{id, name, capacity, sold}]`.
- `POST /api/events {name, capacity}` -> `201 {id, name, capacity, sold:0}`.
- `POST /api/bookings {eventId, idempotencyKey?}` + header interno `X-User-Id` -> `201 {id, eventId, userId, status:PENDING}`.
  Error `404` si evento inexistente, `409` si `idempotencyKey` duplicado, `400` si validación falla.
- `GET /api/bookings/me` + `X-User-Id` -> `200 [...]` solo del usuario.
- Entidades: `events(id UUID, name, capacity, sold)`, `bookings(id UUID, event_id, user_id, status, idempotency_key unique)`.
- `BookingStatus`: `PENDING` (este día; `PAID`/`REJECTED` llegan en D3).

## 4. Archivos

- Crear `booking-service/pom.xml` (hijo): `web, validation, data-jpa, postgresql, eureka-client`, MapStruct (`mapstruct`, `mapstruct-processor`), tests JUnit5/Mockito.
- Crear `booking-service/src/main/java/com/eventpay/booking/`:
  `domain/model/{Event, Booking, BookingStatus}` (POJO puro, cero Spring),
  `domain/port/in/{CreateBookingUseCase, ListBookingsUseCase}`,
  `domain/port/out/{BookingRepositoryPort, EventPublisherPort}` (el out-publisher se deja sin adapter este día),
  `application/service/BookingService` (implements in-ports, guarda PENDING),
  `infrastructure/in/web/{BookingController, EventController, dto, mapper}`,
  `infrastructure/out/persistence/{BookingEntity, EventEntity, JpaBookingRepo, JpaEventRepo, BookingPersistenceAdapter}`.
- Crear `booking-service/src/main/resources/application.yml` (puerto 8082, datasource `booking-db:5434`).
- Crear `booking-service/Dockerfile`, `BookingServiceApplication.java`.
- Modificar `pom.xml` raíz: añadir `<module>booking-service</module>`.
- Modificar `docker-compose.yml`: descomentar `booking` (con `context: .`).

## 5. Tests

- `BookingServiceTest` (JUnit5 + Mockito, sin Spring): crea PENDING, 404 evento inexistente, 409 idempotencia duplicada, lista solo del usuario.
- Regla hexagonal: el test de dominio no levanta contexto Spring.

## 6. Done verificable

- `mvn verify` en verde.
- `docker compose up --build -d` con booking Up.
- Manual: crear evento -> reservar con `X-User-Id` -> 201 PENDING; repetir `idempotencyKey` -> 409; `GET /api/bookings/me` filtra por usuario.
- Commit pequeño ES `d2-booking-sync`.

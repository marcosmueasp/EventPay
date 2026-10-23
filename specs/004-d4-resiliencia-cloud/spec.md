# 004 — D4 Resiliencia + Cloud (Día 4)

- Estado: pendiente (requiere 003)
- Enlaces: `docs/CONSTITUTION.md §2, §3, §5` · `AGENTS.md` · `MEMORY.md`

## 1. Objetivo

Endurecer el flujo: errores de Kafka van a DLT, reservas idempotentes,
gateway autentica JWT y el estado llega al front por SSE.

## 2. Alcance / No hacer

- Sí: retry 3x + DLT, `Idempotency-Key`, `AuthFilter` JWT->`X-User-Id`, registro Eureka de los 3 servicios, endpoint SSE.
- No: Angular (D5), docs finales (D6), CORS/perfiles (D7 salvo lo mínimo para que SSE funcione en local).

## 3. Contratos

- Kafka: `DefaultErrorHandler` con reintentos 3x + `DeadLetterPublishingRecoverer` a `*.DLT`.
  Un mensaje corrupto en `payment.result` termina en `payment.result.DLT` sin tumbar el listener.
- REST booking: header `Idempotency-Key` con unique en `bookings.idempotency_key`.
  Repetir la misma key -> `409`. Sin key -> se genera una (el alta sigue funcionando).
- Gateway `AuthFilter`: valida JWT HS256 (mismo secret que identity), extrae `sub=userId`,
  propaga `X-User-Id` y devuelve `401` sin token o con token inválido/caducado.
  Rutas: `/auth/**` pública, `/api/**` protegida.
- Eureka: `gateway`, `identity`, `booking` registrados (`eureka.client.service-url.defaultZone=http://eureka:8761/eureka`
  en docker, `http://localhost:8761/eureka` en local).
- SSE: `GET /api/bookings/{id}/stream` -> `text/event-stream` con eventos de cambio de estado
  (`PENDING` -> `PAID`/`REJECTED`). Requiere auth (pasa por el AuthFilter).

## 4. Archivos

- Crear `booking-service/.../out/kafka/config/KafkaErrorHandlerConfig` (retry + DLT).
- Modificar `PaymentResultListener` para integrarse con el error handler (sin try/catch que trague errores).
- Modificar `BookingController`: leer header `Idempotency-Key`, constraint unique ya modelado en D2 + manejo `409`.
- Crear `gateway-service/src/main/java/com/eventpay/gateway/AuthFilter.java` (GatewayFilter JWT->`X-User-Id`).
- Modificar `gateway-service/.../application.yml`: filtros por ruta (`/auth/**` abierta, `/api/**` con AuthFilter), timeouts.
- Modificar los 3 `application.yml`: nombre de app + `eureka.client.*` + `fetch-registry/register-with-eureka: true`.
- Crear `booking-service/.../in/sse/BookingSseController.java` (SseEmitter por bookingId, emite al actualizar estado).
- Modificar `BookingService`/`PaymentResultListener` para notificar al emisor SSE tras actualizar estado.

## 5. Tests

- Sin tests nuevos obligatorios; no romper los existentes (`mvn verify` verde).
- Prueba manual del DLT con mensaje corrupto (ver §6).

## 6. Done verificable

- `mvn verify` en verde.
- `docker compose up --build -d` verde; los 3 servicios visibles en Eureka `http://localhost:8761`.
- Sin token a `/api/**` -> 401; con token de `/auth/login` -> 200 y `X-User-Id` llega a booking.
- Reservar con `Idempotency-Key` repetida -> 409.
- Publicar mensaje corrupto en `payment.result` -> aparece en `payment.result.DLT`, el listener sigue vivo.
- Abrir `GET /api/bookings/{id}/stream` y crear reserva -> se ve `PENDING` pasar a `PAID` en vivo.
- Commit pequeño ES `d4-resiliencia`.

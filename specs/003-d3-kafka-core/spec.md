# 003 — D3 Kafka core (Día 3)

- Estado: pendiente (requiere 002: dominio y alta PENDING estables; no paralelizar D2/D3)
- Enlaces: `docs/CONSTITUTION.md §2, §3, §5` · `AGENTS.md` · `MEMORY.md §4.5`

## 1. Objetivo

Conectar la reserva al flujo asíncrono: al crear `PENDING` se publica `booking.requested`,
un worker simulado espera 2s y publica `payment.result`, que actualiza la reserva a `PAID`/`REJECTED`.

## 2. Alcance / No hacer

- Sí: `KafkaTopicConfig`, publisher en el alta, `PaymentSimWorker`, `PaymentResultListener`,
  test EmbeddedKafka. Serde JSON, sin Avro/Schema Registry.
- No: retry/DLT (D4), AuthFilter (D4), SSE (D4), Angular (D5). No cambiar el dominio.

## 3. Contratos

- `booking.requested {bookingId, eventId, userId, requestedAt}` — clave `eventId`, JSON v1.
  Se publica al crear la reserva (transacción local + publish; sin saga completa a propósito).
- `payment.result {bookingId, status:PAID|REJECTED, reason}` — lo publica el worker simulado tras ~2s.
- `PaymentResultListener` consume `payment.result` y actualiza `bookings.status`.
- Config Kafka local: `kafka:9092` en compose, `localhost:29092` en local. `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`
  vale en D3; los topics se declaran además en `KafkaTopicConfig`.
- `user.created {userId, email}` se deja declarado pero su publicación real puede esperar (no bloquea D3).

## 4. Archivos

- Modificar `booking-service/pom.xml`: añadir `spring-kafka` + `spring-kafka-test` (test).
- Crear `infrastructure/out/kafka/{KafkaBookingPublisher, config/KafkaTopicConfig}`.
  El publisher implementa `EventPublisherPort` del dominio.
- Crear `infrastructure/in/kafka/{PaymentSimWorker, PaymentResultListener}`:
  `PaymentSimWorker` = `@KafkaListener(booking.requested)` -> sleep 2s -> envía `payment.result`;
  `PaymentResultListener` = `@KafkaListener(payment.result)` -> actualiza estado vía puerto.
- Modificar `BookingService` (application): tras guardar PENDING invoca `EventPublisherPort`.
- Crear `application-kafka` props en `application.yml` (bootstrap-servers por perfil `local/docker`, group-id, deserializers JSON, `auto-offset-reset earliest` en test).
- No tocar `docker-compose.yml` salvo que falte una env de Kafka para booking.

## 5. Tests

- Test EmbeddedKafka: crea reserva -> `booking.requested` publicado -> worker responde ->
  `payment.result` consumido -> estado final `PAID` (o `REJECTED` según simulación).
- 1 test kafka en verde dentro de `mvn verify`.

## 6. Done verificable

- `mvn verify` en verde (incluye test EmbeddedKafka).
- Manual con Kafka: crear reserva -> sigue PENDING ~2s -> pasa a PAID sin intervención.
- Commit pequeño ES `d3-kafka`.

# EventPay — MEMORY.md

Memoria operativa del repo. Fuente de verdad: `docs/CONSTITUTION.md`. Reglas de trabajo: `AGENTS.md`.
Idioma: ES en docs y commits.

## 1. Qué es

Demo de reservas con pago asíncrono simulado para portfolio.
Flujo MVP: `Angular :4200 -> Gateway :8080 -> identity :8081 (register/login JWT)` y `-> booking :8082`
-> guarda `PENDING` -> publica `booking.requested` -> worker interno espera 2s -> `payment.result (PAID/REJECTED)`
-> Angular lo ve por SSE sin refrescar.

Estado a 2026-10-23: D1 (identity/gateway) y D2 (booking síncrono) implementados y verificados; el
flujo de pago con Kafka es D3.

## 2. Stack fijado (innegociable)

Java 17 (Temurin 17.0.20.1), Spring Boot 3.2.5, Spring Cloud 2023.0.1, Angular 18 (D5),
Apache Kafka 3.7 KRaft, Postgres 16. Serde JSON, sin Avro/Schema Registry.
Kafka: `kafka:9092` en compose, `localhost:29092` en local.

## 3. Arquitectura y puertos

- `4200` Angular -> `8080` Gateway (única entrada front, nada directo a 8081/8082)
- `/auth/**` -> identity `8081` -> `users-db 5433`
- `/api/**` -> booking `8082` -> `booking-db 5434`. En D2 la API se publica **directamente en `:8082`**:
  el Gateway solo enruta `/auth/**` y no expone `/api/**` hasta D4 (decisión D5).
- Eureka `8761`, Kafka `9092/29092` KRaft single-node (booking no habla con Kafka hasta D3)
- Topics JSON v1 (previstos para D3): `booking.requested {bookingId,eventId,userId,requestedAt}` (key `eventId`),
  `payment.result {bookingId,status:PAID|REJECTED,reason}`, `user.created {userId,email}` + `*.DLT`
- Seguridad: identity BCrypt + JJWT HS256 1h (`sub=userId`). Gateway `AuthFilter` (D4) valida y propaga `X-User-Id`.
  Booking (D2) confía en el header interno y lo parsea en el borde HTTP.
- Hexagonal estricta solo en booking-service: `domain/` sin `org.springframework.*`.

## 4. Decisiones (no repetir errores)

### Pre-D1

1. **Boot 3.2.5 sin Initializr:** `start.spring.io` solo ofrece 4.x (3.2.x EOL, fuera del generador).
   Los artefactos siguen en Maven Central: pom manual con `spring-boot-starter-parent:3.2.5`
   (`pom.xml:3-7`) + `spring-cloud-dependencies:2023.0.1` (`pom.xml:29-39`). No generar con 4.0.8 para luego bajar.
2. **Maven 3.9.9 en `~/.tools`:** instalado vía `archive.apache.org` (fallaba `dlcdn`). Ruta:
   `~/.tools/apache-maven-3.9.9/bin/mvn.cmd`. Quedó en PATH de usuario pero las shells abiertas
   antes no lo heredan: hasta reabrir terminal usar la ruta completa.
3. **Root pom multimódulo (actualizado al cerrar D2):** ya incluye `eureka-server`, `identity-service`,
   `gateway-service` y `booking-service` (`pom.xml`). Cada módulo se añadió en su día (`d1-identity`,
   `d2-booking-sync`) para no romper `mvn verify` ni mezclar servicios en un commit.
4. **Eureka con contexto raíz:** `docker-compose.yml:38-41` usa `context: .` + `dockerfile: ./eureka-server/Dockerfile`
   porque el `Dockerfile` copia el pom padre (`COPY pom.xml /app/pom.xml`). Con `build: ./eureka-server`
   el build falla.
5. **Fix Kafka KRaft:** sin `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` el broker muere con
   `No security protocol defined for listener CONTROLLER`. Fijado en `docker-compose.yml:32-34` con
   `CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT` + `KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER`.
6. **`.gitignore` raíz:** cubre `target/`, `*.class/jar/war`, `node_modules/`, `dist/`, `.angular/`,
   `.idea/` entero, `.vscode/`, `.env`, `*.log`. El `.idea/.gitignore` interno solo vale dentro de `.idea/`.

### D2 (cerrado 2026-10-23)

7. **Dockerfiles de los cuatro módulos:** cada uno copia los poms de **los cuatro** módulos
   (`pom.xml` raíz + eureka/identity/gateway/booking) porque el reactor se resuelve completo antes de
   `-pl booking-service -am`. Si falta uno, el build de la imagen falla.
8. **`saveAndFlush` antes del `UPDATE` condicional:** el insert se vuelca para que la violación de
   unicidad de `idempotency_key` salte antes del `update ... where sold < capacity`; así el `409` de
   idempotencia gana al de aforo (RF10) y el rollback revierte la reserva si no hay plaza.
9. **`ddl-auto: update` + enum de un valor:** Hibernate crea
   `bookings_status_check CHECK (status = 'PENDING')`. Al añadir `PAID`/`REJECTED` en D3 hay que
   revisarlo: `update` no recrea el `CHECK`.
10. **Mapper manual, sin MapStruct:** cuatro DTOs pequeños y control total del contrato JSON; MapStruct
    es una dependencia fuera del stack aprobado.
11. **Sin Testcontainers ni H2 en D2:** la persistencia y la concurrencia se verifican a mano contra
    Postgres (ver comandos). Los tests de adaptador usan mocks.
12. **Precedencia y borde:** los `400` los produce el borde HTTP (Bean Validation + parseo del header
    `X-User-Id`, declarado `required = false` para mantener el formato `{error, message}`); `404`/`409`
    se deciden en `BookingService` en orden fijo. El Gateway no se toca en D2.
13. **Clave en blanco -> `NULL`:** `idempotency_key` anulable con `unique`; varias reservas sin clave no
    colisionan (guardar `""` haría que la segunda devolviera `409`).

## 5. Estado verificado (cierre D2, 2026-10-23)

- `mvn verify`: BUILD SUCCESS con 5 módulos (`eventpay-parent`, `eureka-server`, `identity-service`,
  `gateway-service`, `booking-service`) y **74 tests** en booking-service (dominio, servicios,
  adaptadores, arquitectura y `@WebMvcTest`).
- **D1 implementado:** register/login JWT en identity `8081` (`/auth/register`, `/auth/login`) y
  Gateway `8080` con ruta `/auth/**`; el compose levanta `gateway` e `identity`.
- **D2 cerrado:** `booking-service` hexagonal en `8082`; `GET/POST /api/events`, `POST /api/bookings`
  y `GET /api/bookings/me` (header interno `X-User-Id`); `sold` con escritura condicional; idempotencia
  global con clave única anulable y precedencia `400 -> 404 -> 409 idem -> 409 aforo`. Demo por `:8082`.
- Batería manual §11.3: 42/42 checks en verde (eventos, aforo en 1 y 2, idempotencia, precedencia,
  header inválido, validaciones, `/me` filtrado e invariante `0 <= sold <= capacity`).
- Concurrencia real (10 altas simultáneas, lanzadas en <16 ms): evento `capacity: 3` -> exactamente
  **3×201 y 7×409** de aforo, `sold = 3` y 3 reservas persistidas; 10 altas con la **misma** clave ->
  **1×201 y 9×409** de idempotencia y una sola fila. Ningún evento con `sold > capacity` ni `sold < 0`.
- Persistencia (RF14): tras reiniciar el servicio se conservan eventos y reservas, y la clave usada
  sigue devolviendo `409` de idempotencia.
- Con Kafka parado (RF16) la API responde con normalidad y `dependency:tree` no contiene `kafka`.
- Entorno local de este equipo: `eureka`, `kafka` y `postgres-users` parados e `identity` en
  crash-loop por falta de `JWT_SECRET`/`postgres-users`; booking arranca y responde sin Eureka (solo WARN).

## 6. Comandos

- Backend: `~/.tools/apache-maven-3.9.9/bin/mvn.cmd verify` (tras reabrir terminal basta `mvn verify`).
- Tests de booking: `mvn -pl booking-service test` (o `-Dtest=BookingServiceTest` para uno solo).
- Arrancar booking en local: `DB_HOST=localhost DB_PORT=5434 EUREKA_HOST=localhost java -jar
  booking-service/target/booking-service-0.0.1-SNAPSHOT.jar` (antes `docker compose up -d postgres-booking`).
- Infra: `docker compose up --build -d` / `docker compose ps` / `docker compose logs --tail=20` (+ servicio).
- Front (D5): `npm install && ng serve` (4200 -> Gateway 8080).
- Concurrencia (sin Testcontainers): 10 peticiones HTTP simultáneas con clientes en paralelo
  (`Start-Job`, `HttpClient` o varios `curl` en procesos concurrentes), cada una con su `X-User-Id` y
  clave distinta; después `select count(*) from events where sold > capacity or sold < 0` -> `0`.

## 7. Siguiente (D3)

Kafka: `booking.requested` -> worker simulado -> `payment.result` + DLT (spec 003). Booking **no** trae
todavía nada de Kafka: sin `KafkaTemplate`, sin `@KafkaListener`, sin topics y sin dependencia
`spring-kafka` (verificado en T9 con `dependency:tree` y greps). Antes de añadir `PAID`/`REJECTED`,
revisar el `CHECK (status = 'PENDING')` que genera `ddl-auto: update`. Mantener
`docker compose up --build` en verde y commit pequeño ES (`d3-kafka`).

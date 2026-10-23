# EventPay — MEMORY.md

Memoria operativa del repo. Fuente de verdad: `docs/CONSTITUTION.md`. Reglas de trabajo: `AGENTS.md`.
Idioma: ES en docs y commits.

## 1. Qué es

Demo de reservas con pago asíncrono simulado para portfolio.
Flujo MVP: `Angular :4200 -> Gateway :8080 -> identity :8081 (register/login JWT)` y `-> booking :8082`
-> guarda `PENDING` -> publica `booking.requested` -> worker interno espera 2s -> `payment.result (PAID/REJECTED)`
-> Angular lo ve por SSE sin refrescar.

## 2. Stack fijado (innegociable)

Java 17 (Temurin 17.0.20.1), Spring Boot 3.2.5, Spring Cloud 2023.0.1, Angular 18 (D5),
Apache Kafka 3.7 KRaft, Postgres 16. Serde JSON, sin Avro/Schema Registry.
Kafka: `kafka:9092` en compose, `localhost:29092` en local.

## 3. Arquitectura y puertos

- `4200` Angular -> `8080` Gateway (única entrada front, nada directo a 8081/8082)
- `/auth/**` -> identity `8081` -> `users-db 5433`
- `/api/**` -> booking `8082` -> `booking-db 5434`
- Eureka `8761`, Kafka `9092/29092` KRaft single-node
- Topics JSON v1: `booking.requested {bookingId,eventId,userId,requestedAt}` (key `eventId`),
  `payment.result {bookingId,status:PAID|REJECTED,reason}`, `user.created {userId,email}` + `*.DLT`
- Seguridad: identity BCrypt + JJWT HS256 1h (`sub=userId`). Gateway `AuthFilter` valida y propaga `X-User-Id`.
  Booking confía en header interno.
- Hexagonal estricta solo en booking-service: `domain/` sin `org.springframework.*`.

## 4. Decisiones Pre-D1 (no repetir errores)

1. **Boot 3.2.5 sin Initializr:** `start.spring.io` solo ofrece 4.x (3.2.x EOL, fuera del generador).
   Los artefactos siguen en Maven Central: pom manual con `spring-boot-starter-parent:3.2.5`
   (`pom.xml:3-7`) + `spring-cloud-dependencies:2023.0.1` (`pom.xml:29-39`). No generar con 4.0.8 para luego bajar.
2. **Maven 3.9.9 en `~/.tools`:** instalado vía `archive.apache.org` (fallaba `dlcdn`). Ruta:
   `~/.tools/apache-maven-3.9.9/bin/mvn.cmd`. Quedó en PATH de usuario pero las shells abiertas
   antes no lo heredan: hasta reabrir terminal usar la ruta completa.
3. **Root pom unimódulo de momento:** solo `<module>eureka-server</module>` (`pom.xml:19-22`).
   `gateway/identity/booking` se añaden en D1/D2 para no romper `mvn verify` ni mezclar servicios en un commit.
4. **Eureka con contexto raíz:** `docker-compose.yml:38-41` usa `context: .` + `dockerfile: ./eureka-server/Dockerfile`
   porque el `Dockerfile` copia el pom padre (`COPY pom.xml /app/pom.xml`). Con `build: ./eureka-server`
   el build falla.
5. **Fix Kafka KRaft:** sin `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` el broker muere con
   `No security protocol defined for listener CONTROLLER`. Fijado en `docker-compose.yml:32-34` con
   `CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT` + `KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER`.
6. **`.gitignore` raíz:** cubre `target/`, `*.class/jar/war`, `node_modules/`, `dist/`, `.angular/`,
   `.idea/` entero, `.vscode/`, `.env`, `*.log`. El `.idea/.gitignore` interno solo vale dentro de `.idea/`.

## 5. Estado verificado (cierre Pre-D1)

- `mvn verify`: BUILD SUCCESS (parent + eureka-server).
- `docker compose ps`: 4/4 Up — `postgres-users:5433`, `postgres-booking:5434`,
  `kafka:9092/29092` (BrokerServer STARTED), `eureka:8761` (200 en `/`).
- `docker compose config`: válido. `gateway/identity/booking` siguen comentados (`docker-compose.yml:45-57`).
- La app NO arranca aún: solo infra. Sin login/reserva/PAID hasta D1/D2.

## 6. Comandos

- Backend: `~/.tools/apache-maven-3.9.9/bin/mvn.cmd verify` (tras reabrir terminal basta `mvn verify`).
- Infra: `docker compose up --build -d` / `docker compose ps` / `docker compose logs --tail=20` (+ servicio).
- Front (D5): `npm install && ng serve` (4200 -> Gateway 8080).

## 7. Siguiente (D1)

Scaffold `identity-service` (register/login JWT + test Mockito) + `gateway-service` (route `/auth/**`),
añadir ambos módulos al pom raíz y descomentarlos en compose. Mantener `docker compose up --build` en verde
y commit pequeño ES (`d1-identity`).

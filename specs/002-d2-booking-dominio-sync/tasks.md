# 002 — Tasks D2

RF: RF1 alta de evento · RF2 catálogo · RF3 validación de evento · RF4 alta de reserva PENDING +
`sold` · RF5 evento inexistente · RF6 aforo lleno · RF7 idempotencia global · RF8 validación de
reserva · RF9 header inválido · RF10 precedencia · RF11 mis reservas · RF12 identidad del header ·
RF13 único PENDING · RF14 persistencia/unicidad · RF15 sin sobreventa · RF16 sin identity/Kafka.

- [x] **T1. Módulo y esqueleto de `booking-service`.**
  `pom.xml` hijo (web, validation, data-jpa, postgresql runtime, eureka-client,
  `spring-boot-maven-plugin`, tests con `spring-boot-starter-test` + `archunit-junit5`),
  `BookingServiceApplication`, `application.yml` (8082, `DB_HOST`/`DB_PORT`, `ddl-auto: update`,
  `EUREKA_HOST`) y `<module>booking-service</module>` en el pom raíz.
  RF: todos (base de RF1–RF16), RNF1, RNF4, RNF5.
  Hecho cuando: `mvn -N validate` lista 4 módulos y `mvn -pl booking-service -am package -DskipTests`
  termina en `BUILD SUCCESS` generando el jar ejecutable.

- [x] **T2. Infra Docker sin romper lo que ya funciona.**
  `booking-service/Dockerfile` multistage (copia el pom padre y los **cuatro** poms de módulo,
  `-pl booking-service -am`, `EXPOSE 8082`); añadir
  `COPY booking-service/pom.xml /app/booking-service/pom.xml` a `eureka-server/Dockerfile`,
  `identity-service/Dockerfile` y `gateway-service/Dockerfile`; descomentar `booking` en
  `docker-compose.yml` con `context: .`, `environment` (`DB_HOST`, `DB_PORT`, `EUREKA_HOST`),
  `depends_on: [postgres-booking, eureka]` y `restart: unless-stopped`, **sin `kafka`**.
  RF: RNF5, RF16.
  Hecho cuando: `docker compose config` no da errores y `docker compose build eureka identity
  gateway booking` termina con las cuatro imágenes construidas (sin el ajuste de los tres
  Dockerfiles el build falla).

- [x] **T3. Dominio puro + regla de aforo + ArchUnit (test primero).**
  `domain/model/{Event, Booking, BookingStatus}`, excepciones de dominio y
  `HexagonalArchitectureTest`; `EventTest` con los bordes de `hasCapacity`, `available` y
  `reserve()`.
  RF: RF4, RF6, RF13, RF15, RNF2.
  Hecho cuando: `mvn -pl booking-service test -Dtest=EventTest,HexagonalArchitectureTest` en verde y
  `grep -RIn "org.springframework" booking-service/src/main/java/com/eventpay/booking/domain` sin
  resultados.

- [x] **T4. Puertos y tests de servicios en rojo.**
  `domain/port/in/*`, `domain/port/out/{BookingRepositoryPort, EventRepositoryPort}` (sin puerto de
  publicación), `BookingServiceTest` y `EventServiceTest` con Mockito: alta PENDING, 404, aforo,
  idempotencia, clave en blanco, precedencia de RF10, listado y `userId` del header.
  RF: RF1–RF13.
  Hecho cuando: los tests compilan y fallan **por falta de implementación** (rojo esperado), no por
  error de compilación: `mvn -pl booking-service test -Dtest=BookingServiceTest,EventServiceTest`
  muestra los casos en rojo.

- [x] **T5. Servicios de aplicación en verde.**
  `EventService` (alta `sold = 0`, listado) y `BookingService` (`@Transactional`, precedencia
  400–404–409 idem–409 aforo, `normalizeKey`, `PENDING`).
  RF: RF1–RF5, RF7–RF10, RF12, RF13, RF15.
  Hecho cuando: `mvn -pl booking-service test -Dtest=BookingServiceTest,EventServiceTest` en verde,
  con el caso de precedencia (lleno + clave repetida -> idempotencia) incluido.

- [x] **T6. Persistencia con escritura condicional (test del adaptador primero).**
  `EventEntity`, `BookingEntity` (`idempotency_key` único y anulable), `JpaBookingRepo`,
  `JpaEventRepo` (`@Modifying` JPQL `update ... set sold = sold + 1 where id = :id and sold <
  capacity`), `BookingPersistenceAdapter` (`saveAndFlush` + filas afectadas -> `EventFullException`;
  `DataIntegrityViolationException` -> `IdempotencyConflictException`); antes, `BookingPersistenceAdapterTest`
  con mocks (0 filas / 1 fila / violación de unicidad / clave nula).
  RF: RF4, RF7, RF14, RF15.
  Hecho cuando: `mvn -pl booking-service test -Dtest=BookingPersistenceAdapterTest` en verde y
  `mvn verify` en verde.

- [x] **T7. API REST, validación y errores.**
  `EventController` y `BookingController` (404/409/400 de §5 de la spec), DTOs record con
  `@NotBlank`/`@NotNull`/`@Min(1)`/`@Size(max = 100)`, `parseUserId` del header a UUID, mappers
  manuales y `GlobalExceptionHandler` con los mensajes literales
  (`"Evento sin plazas disponibles"`, `"Petición ya procesada: clave de idempotencia repetida"`).
  RF: RF1–RF12.
  Hecho cuando: `mvn verify` en verde y, con `docker compose up -d postgres-booking` y la app
  arrancada, `curl -s localhost:8082/api/events` devuelve `200 []`.

- [x] **T8. Batería manual funcional (§11.3 de la spec).**
  Eventos sin header (`201`), reserva `201 PENDING`, `sold` subiendo en `GET /api/events`, `capacity:
  1` y segunda reserva `409` de aforo, `capacity: 2` y tercera `409`, idempotencia (repetida y con
  otro `eventId`), clave de 101 caracteres `400`, clave `""` `201`, precedencia (header inválido +
  lleno `400`, inexistente + clave repetida `404`, lleno + clave repetida `409` idempotencia),
  sin header / header no-UUID `400`, evento inválido `400`, JSON malformado `400`, `GET
  /api/bookings/me` filtrado.
  RF: RF1–RF12.
  Hecho cuando: todos los checks anteriores dan el código y el mensaje esperados y ningún evento
  queda con `sold > capacity` ni `sold < 0`.

- [x] **T9. Persistencia, concurrencia y ausencias (§11.4–§11.8 de la spec).**
  `docker compose restart booking` y comprobar datos y `409` de idempotencia; 10 altas en paralelo
  sobre un evento `capacity: 3` -> 3 `201` + 7 `409` y `sold: 3`; `docker compose stop kafka` y
  repetir la batería; greps de Kafka/SSE/AuthFilter y de secretos.
  RF: RF14, RF15, RF16, RNF2, RNF4.
  Hecho cuando: tras reiniciar, `GET /api/events` y `/api/bookings/me` conservan los datos y la clave
  usada sigue devolviendo `409`; las 10 paralelas dan exactamente 3 `201` y 7 `409` con `sold: 3`;
  con Kafka parado la batería sigue en verde; `mvn -pl booking-service dependency:tree | grep -i
  kafka` no imprime nada y los greps de `@KafkaListener|KafkaTemplate|springframework.kafka`,
  `SseEmitter|AuthFilter|JwtParser|io.jsonwebtoken` y `JWT_SECRET|secret` no devuelven resultados.

- [x] **T10. Cierre documental y commit.**
  `AGENTS.md` (D2 incluye idempotencia; D4 sin idempotencia), `MEMORY.md` (D2 cerrado, decisiones de
  aforo/precedencia y desviación del calendario), `Estado: implementada` en
  `specs/002-d2-booking-dominio-sync/spec.md` y commit en español.
  RF: ninguno funcional (trazabilidad).
  Hecho cuando: `git diff --stat` muestra solo `AGENTS.md`, `MEMORY.md`, la spec y lo previsto por el
  día; `git log -1 --oneline` es `d2-booking-sync (specs/002-d2-booking-dominio-sync)`.

> **Nota:** son 10 tareas (el máximo acordado) y la spec no se parte: el día es coherente y cada
> tarea cabe en 20–30 minutos. Si T2 o T9 se alargan en la práctica, se ejecutan tal cual pero se
> anota el desvío en `MEMORY.md` al cerrar.

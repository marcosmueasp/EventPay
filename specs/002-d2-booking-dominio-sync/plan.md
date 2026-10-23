# 002 — Plan de implementación (D2)

Deriva de `spec.md` (revisión 2, aprobada). Requisitos funcionales usados abajo: **RF1** alta de
evento sin header · **RF2** catálogo sin header · **RF3** validación de evento · **RF4** alta de
reserva `PENDING` + `sold+1` · **RF5** evento inexistente `404` · **RF6** aforo lleno `409` ·
**RF7** idempotencia global `409` · **RF8** validación de reserva · **RF9** header inválido `400` ·
**RF10** precedencia `400 → 404 → 409 idem → 409 aforo` · **RF11** mis reservas · **RF12** identidad
solo del header · **RF13** único estado `PENDING` · **RF14** persistencia + unicidad ·
**RF15** última plaza sin sobreventa · **RF16** sin llamadas salientes a identity/Kafka.

## 1. Archivos y responsabilidad (RF que cubre cada uno)

Arquitectura hexagonal estricta solo aquí: `domain/` (POJO puro, sin `org.springframework.*`),
`application/` (casos de uso, orquesta dominio y puertos), `infrastructure/in/web` (REST),
`infrastructure/out/persistence` (JPA). El flujo de dependencias va siempre hacia dentro.

Crear:

- `booking-service/pom.xml` — hijo de `eventpay-parent`: `web`, `validation`, `data-jpa`,
  `postgresql` (runtime), `eureka-client`; `spring-boot-maven-plugin` (jar ejecutable para el
  `Dockerfile`, igual que identity); test scope con `spring-boot-starter-test` (JUnit5 + Mockito) y
  `archunit-junit5`. Sin MapStruct y sin `spring-kafka` (RF1–RF16, RNF1, RF16).
- `booking-service/src/main/java/com/eventpay/booking/BookingServiceApplication.java` — arranque
  (RNF5).
- `domain/model/Event.java` — campos `id`, `name`, `capacity`, `sold`; comportamiento
  `hasCapacity()`, `available()` y `reserve()` que devuelve una copia con `sold+1` o lanza
  `EventFullException`; invariante `0 <= sold <= capacity` (RF1, RF4, RF6, RF13, RF15).
- `domain/model/Booking.java` — `id`, `eventId`, `userId`, `status`, `idempotencyKey` (nullable);
  estado inicial `PENDING` (RF4, RF12, RF13, RF14).
- `domain/model/BookingStatus.java` — enum con `PENDING` (RF13).
- `domain/exception/{EventNotFoundException, EventFullException, IdempotencyConflictException,
  InvalidHeaderException}.java` — excepciones Java puras, sin anotaciones Spring, para que el
  dominio y la aplicación no dependan del framework web (RF5–RF9, RNF2).
- `domain/port/out/{BookingRepositoryPort, EventRepositoryPort}.java` — persistencia:
  `findById`, `findAll`, `save`, `existsByKey`, `findByUserId`, `saveReservingPlace` (RF1–RF7, RF11,
  RF14, RF15). **No** hay puerto de publicación: llega con la spec 003 (RF16).
- `domain/port/in/{CreateBookingUseCase, ListBookingsUseCase, CreateEventUseCase, ListEventsUseCase}.java`
  — contratos de los casos de uso (RF1–RF4, RF11).
- `application/service/BookingService.java` — implementa los puertos de entrada, fija la precedencia
  de RF10, normaliza la clave, marca la transacción y delega la escritura atómica en el puerto
  (RF4–RF10, RF12, RF13, RF15).
- `application/service/EventService.java` — alta con `sold = 0` y listado (RF1–RF3).
- `infrastructure/in/web/BookingController.java` — `POST /api/bookings` y `GET /api/bookings/me`;
  parsea `X-User-Id` a UUID y delega (RF4, RF9, RF11, RF12).
- `infrastructure/in/web/EventController.java` — `GET /api/events` y `POST /api/events`, sin header
  (RF1, RF2).
- `infrastructure/in/web/dto/{CreateEventRequest, EventResponse, CreateBookingRequest,
  BookingResponse, ErrorBody}.java` — records como en identity; validación `@NotBlank`, `@NotNull`,
  `@Min(1)`, `@Size(max = 100)` (RF1–RF3, RF8, RF11).
- `infrastructure/in/web/mapper/{EventMapper, BookingMapper}.java` — mapeo **manual** dominio ↔ DTO
  (`toResponse(Event)`, `toResponse(Booking)`, `toList(...)`), sin MapStruct (RF1, RF2, RF4, RF11).
- `infrastructure/in/web/GlobalExceptionHandler.java` — `@RestControllerAdvice` que traduce
  validación, header inválido, no encontrado y ambos conflictos al formato `{error, message}` de 001,
  con los mensajes literales de la spec (RF3, RF5–RF10).
- `infrastructure/out/persistence/{EventEntity, BookingEntity, JpaEventRepo, JpaBookingRepo,
  BookingPersistenceAdapter}.java` — tablas de §5 de la spec; `BookingEntity.idempotencyKey` con
  `unique` y nullable; `JpaEventRepo.incrementSoldIfAvailable(UUID)` como `@Modifying` JPQL que
  devuelve filas afectadas; el adaptador implementa `BookingRepositoryPort` con inserción +
  actualización condicional en la misma transacción (RF4, RF7, RF14, RF15).
- `booking-service/src/main/resources/application.yml` — puerto 8082; datasource
  `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5434}/booking-db` con credenciales de desarrollo
  `eventpay`/`eventpay` (igual que 001 y el compose); `ddl-auto: update`; cliente Eureka con
  `EUREKA_HOST` (RNF4, RNF5).
- `booking-service/Dockerfile` — multistage como eureka/identity: copia `pom.xml` y **los cuatro**
  poms de módulo, `mvn -q -pl booking-service -am package -DskipTests`, `EXPOSE 8082` (RNF5).
- Tests: `EventTest.java` (dominio, sin Spring), `BookingServiceTest.java`, `EventServiceTest.java`,
  `BookingPersistenceAdapterTest.java` (Mockito), `HexagonalArchitectureTest.java` (ArchUnit) —
  detalle y RF en §6.

Modificar:

- `pom.xml` raíz — `<module>booking-service</module>` (todos).
- `eureka-server/Dockerfile`, `identity-service/Dockerfile`, `gateway-service/Dockerfile` — añadir
  `COPY booking-service/pom.xml /app/booking-service/pom.xml` junto a las copias de poms que ya
  tienen (líneas 5-7 de cada uno). Sin esto el reactor padre declara un módulo inexistente en el
  contexto de build y `mvn -pl <módulo> -am` falla (RNF5).
- `docker-compose.yml` — descomentar `booking` con `context: .` +
  `dockerfile: ./booking-service/Dockerfile`, `ports: ["8082:8082"]`,
  `environment: {DB_HOST: postgres-booking, DB_PORT: 5432, EUREKA_HOST: eureka}`,
  `depends_on: [postgres-booking, eureka]` (sin `kafka`) y `restart: unless-stopped` (RNF4, RNF5,
  RF16).
- `AGENTS.md` y `MEMORY.md` — solo en la tarea de cierre (idempotencia en D2, estado cerrado,
  desviación del calendario).

No se modifica `gateway-service` en código ni rutas (decisión D5, §5.7).

## 2. Funciones puras y comportamiento de dominio

**No hay fechas ni cálculos temporales en D2: N/A.** La spec no pide `requestedAt`, ni ventanas, ni
rachas; por eso no hay parámetro `hoy`/`now` en esta spec y no se introduce ninguno. El
`booking.requested.requestedAt` es de la spec 003 y allí se decide.

Comportamiento de dominio testeable sin Spring (RF entre paréntesis):

- `Event.hasCapacity() -> sold < capacity` (RF6, RF15).
- `Event.available() -> capacity - sold` (RF2; apoyo de RF15).
- `Event.reserve() -> Event` copia con `sold + 1`, o `throw EventFullException` si no hay plazas
  (RF4, RF6, RF15). Es la única regla de negocio del día y vive en dominio, no en el controlador.
- `Booking.pending(...)` fábrica que fija `status = PENDING` y nunca otro (RF4, RF13).
- `BookingService.normalizeKey(String) -> String | null` — `trim()`; si queda vacío devuelve `null`
  (clave ausente). Es estática y package-private: se prueba directamente en `BookingServiceTest`
  (RF7, RF8).
- `parseUserId(String) -> UUID` — `UUID.fromString` envuelto; fallo -> `InvalidHeaderException`
  (RF9). Vive en el controlador porque es borde HTTP, no negocio.

## 3. Algoritmos en pseudocódigo

Alta de evento (RF1–RF3):
`validación Bean Validation (`name` `@NotBlank`, `capacity` `@NotNull @Min(1)`);`
`si falla -> 400 {bad_request, ...} antes de entrar al servicio;`
`evento = EventService.create(name, capacity) -> save(new Event(uuid(), name, capacity, sold=0));`
`responder 201 {id, name, capacity, sold: 0};`

Alta de reserva (RF4–RF10, RF15). El orden de los pasos **es** RF10:

1. `Borde HTTP (400):` `@Valid` sobre el cuerpo y `parseUserId(header)`; si algo falla se responde
   `400` sin llamar al servicio.
2. `event = events.findById(eventId) o throw EventNotFoundException` -> `404` (RF5).
3. `key = normalizeKey(rawKey); si key != null y bookings.existsByKey(key) -> throw
   IdempotencyConflictException` -> `409` idempotencia (RF7).
4. `si !event.hasCapacity() -> throw EventFullException` -> `409` aforo (RF6).
5. `booking = bookings.saveReservingPlace(Booking.pending(uuid(), eventId, userId, key))` (RF4).
6. `responder 201 {id, eventId, userId, status: PENDING}` (RF4).

`saveReservingPlace` (adaptador, dentro de la transacción del servicio) (RF4, RF7, RF15):
`saveAndFlush(bookingEntity)` — el `unique` de `idempotency_key` decide las carreras de idempotencia;
`si DataIntegrityViolation -> throw IdempotencyConflictException;`
`filas = events.incrementSoldIfAvailable(eventId)` — `UPDATE events SET sold = sold + 1
WHERE id = ? AND sold < capacity`;`
`si filas == 0 -> throw EventFullException (la transacción revierte y NO queda la reserva);`
`return booking;`

Inserta antes de actualizar a propósito: así, cuando dos condiciones chocan a la vez, gana siempre
el `409` de idempotencia (RF10) y el aforo no deja reservas huérfanas.

Listado de reservas propias (RF11, RF12): `userId = parseUserId(header); return
bookings.findByUserId(userId).map(toResponse)`.

Catálogo (RF2): `events.findAll().map(toResponse)`; sin header.

## 4. Interfaz

N/A en D2 por decisión de la spec (§2: sin Angular ni SSE). No se pinta nada: la "interfaz" del día
son los JSON de §5 de la spec, verificados con `.http`/curl contra `localhost:8082` (RF1–RF12). El
front llega en la spec 005.

## 5. Decisiones técnicas y alternativa descartada

1. **Aforo por escritura condicional + inserción previa con `unique`** (RF15, RF4, RF7). El
   `UPDATE ... WHERE sold < capacity` y la comprobación de filas afectadas es lo único que impide la
   sobreventa con dos transacciones simultáneas; el `if` previo del servicio es solo una lectura
   orientativa (y es lo que NO cumple RF15). Se inserta la reserva primero para que una clave
   repetida gane siempre al aforo (RF10) y la tx revierta si no hay plaza.
   Descartado: **bloqueo pesimista** (`SELECT ... FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)`), que
   serializa todas las reservas del evento y añade fricción sin mejorar la garantía; y **`@Version`
   optimista**, que obliga a reintentos explícitos y ensucia el servicio. También descartado
   apoyarse en un `CHECK (sold <= capacity)`: con `ddl-auto: update` Hibernate no lo crea y el error
   llegaría como excepción genérica sin mensaje de negocio.
2. **Precedencia en la capa de aplicación** (RF10). Los `400` los produce el borde HTTP (Bean
   Validation + parseo del header, que Spring ejecuta antes del método), y los `404`/`409` se evalúan
   en `BookingService` con sentencias en orden explícito; el handler solo traduce excepciones a HTTP.
   Descartado: decidir la precedencia en el controlador (mete negocio en la capa web) o confiar en el
   orden de las restricciones de base de datos (no determinista y no testeable sin BD).
3. **Clave en blanco -> `NULL` en base de datos** (RF7). `idempotency_key` es anulable con `unique`:
   Postgres permite muchos `NULL` y varias reservas sin clave no colisionan.
   Descartado: guardar `""` para las claves ausentes (el `unique` haría que la segunda reserva sin
   clave devolviera `409`).
4. **Mapper manual** (RF1, RF2, RF4, RF11). Cuatro DTOs pequeños y control total del contrato JSON.
   Descartado: MapStruct, dependencia fuera del stack aprobado en 001 §2 y que obliga a procesador
   de anotaciones y configuración extra.
5. **`ddl-auto: update`** (RF14, RNF5). Coherencia con 001 y cero fricción antes de tener datos.
   Descartado: Flyway/Liquibase en D2 (más piezas de las que el día necesita).
6. **Transacción en `BookingService.createBooking`** (`@Transactional`), no en el adaptador
   (RF15). Así inserción, actualización condicional y rollback son una sola unidad y el adaptador
   puede sustituirse sin cambiar la semántica. Descartado: transacción en el adaptador (mezcla capas
   y hace que el rollback dependa de la propagación).
7. **El gateway no se toca** (decisión D5 de la spec). Sin AuthFilter, publicar `/api/**` expondría
   reservas sin identidad real y mezclaría dos servicios en un commit. Descartado: añadir la ruta en
   D2 (se hará en D4 junto con el AuthFilter).
8. **Reactor y Dockerfiles en el mismo día** (RNF5, h1). Al añadir el módulo al pom raíz, los tres
   `Dockerfile` existentes deben copiar `booking-service/pom.xml` porque el modelo del reactor se
   resuelve completo antes de `-pl`. Descartado: `COPY . .` en los Dockerfiles (rompe caché de capas
   y sube el contexto) y compilar con `-N`/poms sueltos (deja de reproducir lo que hace `mvn verify`).

## 6. Estrategia de tests (principio 4: sin dependencias nuevas)

Todo dentro de `mvn verify`; nada de Testcontainers, H2 ni EmbeddedKafka en D2.

- `EventTest` (dominio puro, sin Spring): `hasCapacity` en los bordes (`sold = capacity-1`,
  `sold = capacity`), `reserve()` incrementa, `reserve()` en evento lleno lanza `EventFullException`,
  `available()` no baja de 0 [RF4, RF6, RF15].
- `BookingServiceTest` (Mockito, sin contexto Spring): alta `PENDING` con `sold+1` [RF4]; evento
  inexistente -> `EventNotFoundException` [RF5]; aforo lleno -> `EventFullException` sin llamar al
  puerto [RF6]; clave repetida (mismo y distinto `eventId`) -> `IdempotencyConflictException`
  [RF7]; clave `null`/`""`/`"   "` -> viaja `null` al puerto y no consulta `existsByKey` [RF7];
  precedencia [RF10]: header inválido + inexistente -> `InvalidHeaderException`; inexistente + clave
  repetida -> `EventNotFoundException`; lleno + clave repetida -> `IdempotencyConflictException`;
  listado filtrado por usuario y `userId` tomado del header [RF11, RF12]; estado siempre `PENDING`
  [RF13].
- `EventServiceTest` (Mockito): alta con `sold = 0` [RF1]; listado vacío -> lista vacía [RF2]; los
  `400` de validación de evento [RF3] se cubren en el borde HTTP (Bean Validation), documentado aquí
  como caso manual §11.3 de la spec.
- `BookingPersistenceAdapterTest` (Mockito, sin BD): el adaptador usa `saveAndFlush` +
  `incrementSoldIfAvailable`; casos: 0 filas afectadas -> `EventFullException` (sin devolver
  reserva) [RF15]; 1 fila -> reserva devuelta [RF4]; `saveAndFlush` lanza
  `DataIntegrityViolationException` -> `IdempotencyConflictException` [RF7]; la clave viaja `null`
  cuando no hay clave [RF7]. El rollback real (que no quede reserva cuando no hay plaza) se
  verifica en el check manual de concurrencia [RF15].
- `HexagonalArchitectureTest` (ArchUnit): `domain/**` no depende de `org.springframework..`;
  `application/**` no depende de `infrastructure..` [RNF2].
- Greps y comandos de ausencia de Kafka/SSE/AuthFilter y de secretos: §11.7 y §11.8 de la spec
  [RF16, RNF4].
- Contratos HTTP: batería manual de §11.3 de la spec contra `localhost:8082` [RF1–RF12].

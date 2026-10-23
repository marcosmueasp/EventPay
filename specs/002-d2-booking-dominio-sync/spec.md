# 002 — D2 Booking dominio síncrono (Día 2)

- Estado: implementada (revisión 2, aprobada por el usuario)
- Enlaces: `docs/CONSTITUTION.md` (principios 1, 2, 3, 4, 5, 6) · `AGENTS.md` (§Orden de trabajo, D2) ·
  `MEMORY.md §3` · `specs/001-d1-base-identity-gateway/spec.md` (§2 dependencias, §3 formato de error
  `{error, message}`) · `specs/003-d3-kafka-core/spec.md` (lo que NO entra aquí)
- Revisión: 2 (cierre de los 16 hallazgos del reviewer, 23 de octubre de 2026)

## 1. Objetivo

Que un usuario ya identificado pueda **reservar una plaza de un evento** y **consultar solo sus
reservas**, naciendo la reserva en `PENDING` y sin ningún paso asíncrono. En D2 no se publica ni se
consume nada: el objetivo del día es dejar el dominio de reservas y su API estables y verificables
por HTTP antes de conectar Kafka (D3). Sirve de base para que D3 solo añada el flujo de pago, sin
tocar el dominio.

## 2. Alcance / fuera de alcance

Sí entra en D2:

- Alta y listado de eventos (catálogo mínimo para poder reservar), sin autenticación: endpoints de
  demo/seed.
- Alta de reserva en estado `PENDING`, con control de aforo e idempotencia.
- Listado de las reservas del usuario que hace la petición.
- Persistencia de eventos y reservas en la base de datos del servicio de reservas.
- Dominio puro + puertos de persistencia + adaptador + API REST.
- Alta del servicio en el build raíz y en `docker-compose.yml`, incluido el ajuste de los
  `Dockerfile` que ya existen (ver §9).

Fuera de alcance (no hacer en D2):

- **Kafka en cualquiera de sus formas**: publishers, listeners, topics, worker simulado, DLT/retry,
  serde, dependencias de Kafka. D2 no publica ni consume; no se declara ningún topic ni se crea
  ningún puerto de publicación (el puerto de eventos nace en la spec 003).
- Transiciones a `PAID` / `REJECTED` (llegan con el resultado del pago, spec 003).
- AuthFilter JWT, validación de token y autenticación real en este servicio (spec 004). D2 confía en
  un header interno.
- Ruta nueva `/api/**` en el gateway: el gateway **no se toca** en D2. La demo se prueba contra
  `localhost:8082`; la ruta `/api/**` entra en D4 junto con el AuthFilter (decisión D5).
- SSE, Angular, interceptor de auth y cualquier vista (specs 004 y 005).
- Cancelar o modificar reservas, reembolsos, cupones, precios, editar `capacity` de un evento ya
  creado, liberar plazas (`sold`) cuando una reserva no se paga, paginación, filtros y orden
  garantizado de listados.

Dependencias permitidas (constitución, principio 4 y mismo criterio que 001 §2): solo starters del
stack fijado (Boot 3.2.5 / Cloud 2023.0.1 BOM) + stack de tests (JUnit5, Mockito, ArchUnit). **Sin
MapStruct** (mapper manual) y **sin `spring-kafka`**. Cualquier otra dependencia requiere aprobación.

**Desviación consciente del calendario (idempotencia).** `AGENTS.md` sitúa la idempotencia en D4;
esta spec la incluye en D2 (decisión del usuario) porque el alta de reserva necesita la garantía
desde el primer día y el contrato ya la declara. Al aprobar/cerrar esta spec se actualizarán
`AGENTS.md` (D2 incluye idempotencia del alta) y `MEMORY.md` para que el calendario refleje la
realidad; ese cambio será una tarea de `tasks.md` y no se ejecuta en esta fase.

## 3. Usuarios e historias de usuario

- **Usuario que reserva**: ya existe en identity (D1) y llega identificado por un header interno;
  en D2 no hay login en este servicio.
- **Responsable de la demo / seed**: necesita crear eventos para poder reservar, sin autenticación.
- **Equipo**: necesita poder construir y probar el dominio sin Kafka ni identity levantados.

- HU-1. Como usuario identificado, quiero reservar una plaza de un evento existente para que la
  reserva quede en `PENDING` a la espera del pago.
- HU-2. Como usuario identificado, quiero ver solo mis reservas para no mezclar mis reservas con las
  de otras personas.
- HU-3. Como responsable de la demo, quiero poder crear y listar eventos para tener catálogo antes
  de reservar.

## 4. Definiciones

- **Evento**: unidad reservable, con `name` (texto no vacío) y `capacity` (aforo declarado, entero
  mayor que 0).
- **`sold`**: plazas ocupadas del evento. Empieza en `0` al crear el evento y **se incrementa en uno**
  cada vez que se crea una reserva sobre él (decisión D1). Plazas disponibles = `capacity - sold`.
  Invariante: `0 <= sold <= capacity`, siempre.
- **Reserva (`booking`)**: relación entre un evento y un usuario, con un estado. Una reserva **ocupa
  plaza desde que se crea**, aunque siga en `PENDING`; en D2 no se libera ninguna plaza.
- **`PENDING`**: estado inicial y único de D2 ("pendiente de pago"); `PAID` / `REJECTED` pertenecen a D3.
- **Clave de idempotencia**: texto opcional de hasta 100 caracteres que aporta el cliente en el alta
  de reserva para que repetir la misma petición no cree una segunda reserva. Ausente, vacía o solo
  con espacios equivale a **sin clave** (no participa en la unicidad). Entre claves presentes, la
  unicidad es **global**, no por usuario: la misma clave con otro `eventId` también se rechaza
  (decisión D2). Las reservas sin clave se guardan con la clave a `NULL`.
- **Header interno `X-User-Id`**: identificador del usuario que hace la petición; debe ser un UUID.
  Es obligatorio en `POST /api/bookings` y `GET /api/bookings/me`, y **no** se exige en los endpoints
  de eventos. En D2 lo pone el cliente (el gateway lo pondrá a partir del JWT en D4); este servicio
  confía en él y lo exige: si falta o no es UUID, la petición se rechaza con `400` (decisión D3).
- **Identidad del usuario**: siempre proviene del header interno, nunca del cuerpo de la petición.
- **Conflicto (`409`)**: código único para dos causas — aforo lleno y clave de idempotencia repetida —,
  distinguibles por el `message`, cuyas cadenas son contrato literal (§5). Si el cliente necesita
  distinción legible por máquina, se valorará un campo de código en D5.
- **Precedencia de rechazos**: cuando un alta de reserva incumple varias condiciones a la vez, se
  evalúan en orden fijo `400 -> 404 -> 409 idempotencia -> 409 aforo` y se devuelve solo el primero
  (decisión del usuario en la revisión 2).

## 5. Contratos

- `GET /api/events` -> `200 [{id, name, capacity, sold}]` (`[]` si no hay eventos). **Sin**
  `X-User-Id`: endpoint de demo/seed, abierto en D2; su protección se revisa en la spec 004.
- `POST /api/events {name, capacity}` -> `201 {id, name, capacity, sold: 0}`. **Sin** `X-User-Id`,
  mismo criterio de demo/seed. Errores: `400` si falta `name` o `capacity`, si `name` está vacío, si
  `capacity` no es un entero mayor que 0, o si el cuerpo JSON está ausente o malformado.
- `POST /api/bookings {eventId, idempotencyKey?}` + header interno `X-User-Id` ->
  `201 {id, eventId, userId, status: PENDING}` e incrementa en uno el `sold` del evento.
  `idempotencyKey` es opcional, texto de hasta 100 caracteres; ausente, `""` o solo espacios = sin
  clave.
  Errores, **en este orden fijo de precedencia** (se devuelve solo el primero que se cumpla):
  1. `400` si `X-User-Id` falta, está vacío o no es UUID; o si `eventId` falta o no es UUID; o si
     `idempotencyKey` supera los 100 caracteres; o si el cuerpo está malformado.
  2. `404` si el evento no existe.
  3. `409` si la `idempotencyKey` (presente) ya está usada, con cualquier `eventId`.
  4. `409` si el evento está lleno (`sold >= capacity`).
- Mensajes de los dos `409` (contrato literal, mismo `error: "conflict"`):
  - aforo lleno: `"Evento sin plazas disponibles"`
  - idempotencia: `"Petición ya procesada: clave de idempotencia repetida"`
  Son indistinguibles por código HTTP; se distinguen por el texto exacto del `message`. Un campo de
  código legible por máquina se valorará en D5 si el cliente lo necesita.
- `GET /api/bookings/me` + header interno `X-User-Id` -> `200 [{id, eventId, userId, status}]` con
  las reservas de ese usuario y solo las suyas (`[]` si no tiene ninguna). `400` si el header falta o
  no es UUID.
- Formato de error común (heredado de 001 §3): `{error, message}` con
  `error ∈ {bad_request, not_found, conflict, internal_error}`. Los mensajes van en español.
- Cuerpo JSON ausente o malformado -> `400` (comportamiento por defecto de Spring, sin personalizar),
  igual que en 001.
- Entidades: `events(id UUID, name, capacity, sold)` con el invariante `0 <= sold <= capacity`,
  `bookings(id UUID, event_id, user_id, status, idempotency_key unique, nullable)`; la unicidad
  global solo afecta a claves presentes (las reservas sin clave se guardan con `NULL`).
- `BookingStatus`: `PENDING` (único estado de este día).
- La API se publica tal cual en `localhost:8082`, sin reescritura ni prefijo adicional; el gateway no
  se toca en D2 (decisión D5).

## 6. Requisitos funcionales (EARS)

- **RF1.** CUANDO se recibe un alta de evento con `name` no vacío y `capacity` entero mayor que 0,
  EL SISTEMA crea el evento con un identificador nuevo y `sold = 0`, y responde
  `201 {id, name, capacity, sold: 0}` sin exigir el header `X-User-Id`.
- **RF2.** CUANDO se consulta el catálogo de eventos, EL SISTEMA responde `200` con la lista
  `[{id, name, capacity, sold}]`, y con `[]` si no existe ninguno, sin exigir el header `X-User-Id`.
- **RF3.** SI el alta de evento no trae `name` o `capacity`, o `name` está vacío o solo con espacios,
  o `capacity` no es un entero mayor que 0, o el cuerpo está malformado, ENTONCES EL SISTEMA responde
  `400 {error: "bad_request", message}` y no crea el evento.
- **RF4.** CUANDO se recibe un alta de reserva con un `eventId` existente, con plazas disponibles y
  con el header interno `X-User-Id`, EL SISTEMA crea la reserva en estado `PENDING`, incrementa en uno
  el `sold` del evento y responde `201 {id, eventId, userId, status: PENDING}`, donde `userId` es el
  del header.
- **RF5.** SI el `eventId` no corresponde a ningún evento, ENTONCES EL SISTEMA responde `404` con
  `{error, message}` y no persiste ninguna reserva ni incrementa `sold`.
- **RF6.** SI el evento existe pero ya no le quedan plazas (`sold >= capacity`), ENTONCES EL SISTEMA
  responde `409 {error: "conflict", message: "Evento sin plazas disponibles"}` y no crea la reserva ni
  incrementa `sold`.
- **RF7.** SI llega un alta de reserva con una `idempotencyKey` presente ya registrada en cualquier
  reserva (con el mismo `eventId` o con otro), ENTONCES EL SISTEMA responde
  `409 {error: "conflict", message: "Petición ya procesada: clave de idempotencia repetida"}` y no
  crea una segunda reserva ni incrementa `sold`: sigue existiendo una sola reserva con esa clave. Una
  `idempotencyKey` ausente, vacía o solo con espacios no aplica idempotencia.
- **RF8.** SI el alta de reserva no trae `eventId`, o `eventId` no es un UUID válido, o la
  `idempotencyKey` supera los 100 caracteres, o el cuerpo está malformado, ENTONCES EL SISTEMA
  responde `400` con `{error, message}` y no persiste nada.
- **RF9.** SI el header interno `X-User-Id` falta, está vacío o no es un UUID válido, ENTONCES EL
  SISTEMA responde `400 {error: "bad_request", message}` en cualquier endpoint que lo requiera, sin
  persistir nada ni incrementar `sold`.
- **RF10.** SI un alta de reserva incumple varias condiciones a la vez, ENTONCES EL SISTEMA responde
  solo el primer rechazo según el orden fijo `400` (validación o header) -> `404` (evento inexistente)
  -> `409` (idempotencia repetida) -> `409` (aforo lleno), nunca otro y nunca una combinación.
- **RF11.** CUANDO se consultan las reservas propias con el header `X-User-Id`, EL SISTEMA responde
  `200` con las reservas de ese usuario y solo de ese usuario (`[]` si no tiene ninguna).
- **RF12.** EL SISTEMA toma la identidad del usuario exclusivamente del header interno `X-User-Id` y
  nunca de un `userId` del cuerpo, y la guarda como `user_id` de la reserva.
- **RF13.** MIENTRAS una reserva espera el resultado del pago, EL SISTEMA la mantiene en `PENDING`;
  ninguna operación de D2 cambia el estado de una reserva.
- **RF14.** EL SISTEMA conserva eventos y reservas entre reinicios del servicio y garantiza que no
  puedan existir dos reservas con la misma `idempotencyKey` presente, antes y después de reiniciar.
- **RF15.** SI dos altas de reserva llegan a la vez sobre la última plaza libre, ENTONCES exactamente
  una responde `201` y la otra `409`, con `sold = capacity`, y en ningún caso `sold` supera
  `capacity` ni baja de `0`.
- **RF16.** EL SISTEMA no realiza ninguna llamada saliente a identity ni a Kafka y responde a todas
  las operaciones de §5 con Kafka parado: ninguna operación de D2 depende de ellos, ni publica ni
  consume nada.

## 7. Requisitos no funcionales

- **RNF1.** Verificable en verde con `mvn verify` sin añadir dependencias fuera de las permitidas por
  la constitución (principio 4: JUnit5, Mockito, ArchUnit en scope test; sin EmbeddedKafka en D2).
- **RNF2.** La lógica de dominio no depende del framework web ni de la persistencia ni del reloj del
  sistema: el dominio se puede probar sin levantar contexto (constitución, principio 3).
- **RNF3.** Idioma (constitución, principio 6): identificadores y código en inglés; mensajes de
  error, documentación y commit en español.
- **RNF4.** Sin secretos reales en el repositorio (mismo criterio que 001): el usuario y la contraseña
  de base de datos `eventpay` / `eventpay` son credenciales de desarrollo locales idénticas a las del
  `docker-compose.yml`, y lo único sensible (`JWT_SECRET`) se lee siempre por variable de entorno;
  este servicio no maneja ningún secreto. Verificable con un `grep`: no aparece ningún valor de
  secreto, contraseña distinta de la de desarrollo ni token en `booking-service`.
- **RNF5.** El servicio arranca junto al resto del sistema con `docker compose up --build -d` y queda
  en estado `Up`, escuchando en su puerto, sin romper el build de los servicios ya existentes.

## 8. Casos límite

- Evento inexistente al reservar -> `404`, sin escritura en base de datos (RF5).
- Evento lleno (`sold == capacity`) -> `409 "Evento sin plazas disponibles"`, sin reserva y sin tocar
  `sold` (RF6).
- Última plaza con dos altas simultáneas -> exactamente una `201` y otra `409`, con `sold = capacity`
  y nunca mayor ni negativo (RF15).
- Repetición del alta con la misma `idempotencyKey` -> `409 "Petición ya procesada..."` la segunda vez,
  una sola reserva persistida y `sold` incrementado una sola vez (RF7).
- Misma `idempotencyKey` con otro `eventId` -> `409` igual (unicidad global) (RF7).
- Dos altas simultáneas con la misma `idempotencyKey` -> una responde `201` y la otra `409`, garantizado
  por la restricción de unicidad y no solo por una comprobación previa (RF7, RF14).
- `idempotencyKey` ausente, `""` o solo espacios -> sin clave: cada alta con plazas crea una reserva
  nueva y no colisiona con otras (RF4, RF7).
- `idempotencyKey` de 101 caracteres o más -> `400` por validación, sin reserva (RF8).
- Header `X-User-Id` ausente, vacío o que no es un UUID -> `400 "bad_request"`, tanto en el alta de
  reserva como en el listado de las propias (RF9).
- Cuerpo de alta de reserva con `userId` en el JSON -> se ignora; manda el header (RF12).
- Alta de evento con `capacity` a `0` o negativa, `name` vacío o body malformado -> `400` (RF3).
- Alta y listado de eventos sin `X-User-Id` -> funcionan (`201` / `200`), endpoints de demo/seed (RF1,
  RF2).
- Combinaciones que fijan la precedencia (RF10):
  - header inválido + evento lleno -> `400`.
  - header inválido + evento inexistente -> `400`.
  - evento inexistente + clave repetida -> `404`.
  - evento lleno + clave repetida (clave usada y evento sin plazas) -> `409` de idempotencia.
  - evento lleno + clave nueva -> `409` de aforo.
  - body malformado + evento lleno -> `400`.
- Usuario sin reservas -> `200 []` (RF11).
- Catálogo sin eventos -> `200 []` (RF2).
- `GET /api/events` tras N reservas de un evento -> ese evento devuelve `sold: N` (RF2, RF4).
- Kafka parado -> la API sigue respondiendo (RF16).

## 9. Archivos

Crear:

- `booking-service/pom.xml` (hijo de `eventpay-parent`): `web, validation, data-jpa, postgresql,
  eureka-client`; `spring-boot-maven-plugin` (necesario para que el `Dockerfile` genere el jar
  ejecutable, igual que en identity); tests en scope test con `spring-boot-starter-test`
  (JUnit5 + Mockito) y `archunit-junit5`. **Sin MapStruct** (mapper manual) y **sin `spring-kafka`**,
  que entra en la spec 003 (RNF1, RF16).
- `booking-service/src/main/java/com/eventpay/booking/`:
  - `BookingServiceApplication.java` — arranque del servicio (RNF5).
  - `domain/model/{Event, Booking, BookingStatus}` — POJO puro, cero Spring; `Event` concentra la
    regla de aforo (`sold < capacity`) y el incremento de `sold` (RF1, RF4, RF6, RF13, RF15, RNF2).
  - `domain/port/in/{CreateBookingUseCase, ListBookingsUseCase, CreateEventUseCase, ListEventsUseCase}`
    — casos de uso (RF1–RF4, RF11).
  - `domain/port/out/{BookingRepositoryPort, EventRepositoryPort}` — **solo** puertos de persistencia;
    el puerto de publicación de eventos nace en la spec 003 (RF14, RF15).
  - `application/service/BookingService` — implementa los puertos de entrada, aplica la precedencia de
    rechazos, comprueba aforo e idempotencia y guarda `PENDING` (RF4–RF10, RF12, RF13, RF15).
  - `application/service/EventService` (y su puerto de entrada) — alta y listado de eventos
    (RF1–RF3).
  - `infrastructure/in/web/{BookingController, EventController, dto}` — expone §5 y responde con los
    códigos indicados (RF1–RF12).
  - `infrastructure/in/web/mapper/{BookingMapper, EventMapper}` — mapeo **manual** entre entidades de
    dominio y DTO, sin MapStruct (RF1, RF2, RF4, RF11).
  - `infrastructure/in/web/GlobalExceptionHandler` — mapea validación / header inválido / no
    encontrado / conflicto (aforo e idempotencia, con los mensajes literales de §5) al formato
    `{error, message}` (RF3, RF5–RF10).
  - `infrastructure/out/persistence/{BookingEntity, EventEntity, JpaBookingRepo, JpaEventRepo,
    BookingPersistenceAdapter}` — tablas de §5, `idempotency_key` único y anulable (unicidad global
    solo entre claves presentes) y creación de reserva + incremento de `sold` sin sobreventa
    (RF14, RF15).
- `booking-service/src/main/resources/application.yml` — puerto 8082; datasource por variables
  `DB_HOST` / `DB_PORT` (en compose `postgres-booking:5432`, en local `localhost:5434`), credenciales
  de desarrollo `eventpay` / `eventpay` como en 001; `ddl-auto: update` (mismo criterio que 001);
  `EUREKA_HOST` (RNF4, RNF5).
- `booking-service/Dockerfile` — mismo patrón multistage que los demás módulos: copia el pom padre y
  **los cuatro poms de módulo** (`eureka-server`, `identity-service`, `gateway-service`,
  `booking-service`), `mvn -q -pl booking-service -am package -DskipTests`, `EXPOSE 8082` (RNF5).

Modificar:

- `pom.xml` raíz — añadir `<module>booking-service</module>`.
- `eureka-server/Dockerfile`, `identity-service/Dockerfile`, `gateway-service/Dockerfile` — añadir
  `COPY booking-service/pom.xml /app/booking-service/pom.xml` junto a las copias de poms de módulo
  que ya tienen: el reactor padre pasa a listar cuatro módulos y `mvn -pl <módulo> -am` falla si el
  contexto de build no contiene el pom del módulo declarado (h1). Sin este ajuste, `docker compose
  up --build` deja de estar en verde para servicios que hoy funcionan.
- `docker-compose.yml` — descomentar `booking` con **`context: .` + `dockerfile: ./booking-service/Dockerfile`**
  (mismo criterio que `eureka` / `identity`, porque el Dockerfile copia el pom padre), puerto `8082`,
  `environment` con `DB_HOST: postgres-booking`, `DB_PORT: 5432`, `EUREKA_HOST: eureka`,
  `depends_on: [postgres-booking, eureka]` (**sin `kafka`**, que se añade en D3) y
  `restart: unless-stopped` (RNF4, RNF5, RF16).
- No se toca `gateway-service` a nivel de código ni de rutas en D2 (decisión D5).

Nota de consistencia: el borrador previo fijaba el datasource como `booking-db:5434`, pero el
servicio de base de datos del compose es `postgres-booking` (puerto interno 5432, `5434` solo en el
host) y las bases son `users-db` / `booking-db` (esquema, no host). Aquí se unifica con el patrón de
001 (`DB_HOST`/`DB_PORT` por entorno).

## 10. Tests

- `BookingServiceTest` (JUnit5 + Mockito, sin contexto Spring): alta `PENDING` con incremento de
  `sold`, `404` evento inexistente, `409` evento lleno (`sold == capacity`) sin guardar ni incrementar,
  `409` idempotencia duplicada (misma clave con el mismo y con otro `eventId`), clave ausente/vacía que
  no aplica idempotencia, `400` por `idempotencyKey` de 101 caracteres, precedencia de RF10 (header
  inválido + evento inexistente -> `400`; evento inexistente + clave repetida -> `404`; evento lleno +
  clave repetida -> `409` idempotencia), listado filtrado por usuario y `userId` tomado del header y no
  del cuerpo (RF4–RF12).
- `EventServiceTest` (Mockito, sin Spring): alta con `sold = 0`, validación de `name` vacío,
  `capacity` `0`/negativa y campos ausentes -> `400`, y listado vacío -> `[]` (RF1–RF3).
- `BookingPersistenceAdapterTest` (Mockito, sin base de datos real y sin dependencias nuevas): el
  adaptador resuelve el aforo con una **escritura condicional** (actualización de `sold` que solo
  aplica si `sold < capacity`, comprobando las filas afectadas) y no con un `if` previo. Casos: 0
  filas afectadas -> conflicto de aforo sin insertar reserva; 1 fila afectada -> reserva guardada con
  `sold` incrementado; `idempotency_key` duplicada -> conflicto de idempotencia. Esto es lo que da
  cobertura automática a RF15 dentro de `mvn verify`; la concurrencia real se comprueba a mano en §11.
- Test arquitectónico (ArchUnit en scope test, permitido por constitución §4): `domain/**` no importa
  `org.springframework.*`, y el test de dominio no levanta contexto Spring (RNF2).
- Comprobación de que el build de booking **no** arrastra Kafka en D2 (sin `spring-kafka` ni
  `EmbeddedKafka`; ver comandos en §11) (RF16).
- Contratos de §5 verificados a mano por HTTP (`.http` o curl) en los criterios de finalización
  (RF1–RF12).

## 11. Criterios de finalización

1. `mvn verify` en verde, incluyendo los tests de §10 (`BookingServiceTest`, `EventServiceTest`,
   `BookingPersistenceAdapterTest`, test de arquitectura).
2. El reactor y las imágenes siguen en verde tras añadir el módulo (h1):
   `docker compose config` válido y `docker compose build` correcto para `eureka`, `identity`,
   `gateway` y `booking`; después `docker compose up --build -d` con los siete servicios `Up`
   (`postgres-users`, `postgres-booking`, `kafka`, `eureka`, `identity`, `gateway`, `booking`).
3. Manual contra `localhost:8082`:
   - `POST /api/events` con `{"name":"Demo","capacity":1}` **sin** `X-User-Id` -> `201` con `sold: 0`;
     `GET /api/events` sin header -> `200` con el evento;
   - reservar con `X-User-Id` -> `201` con `status: PENDING`; `GET /api/events` -> `sold: 1`; segunda
     reserva -> `409` `"Evento sin plazas disponibles"` y `sold` sigue en `1`;
   - evento con `capacity: 2` y dos reservas -> `sold: 2`, la tercera -> `409`;
   - repetir la misma `idempotencyKey` -> `409` `"Petición ya procesada: clave de idempotencia
     repetida"`, una sola reserva y `sold` incrementado una sola vez; misma clave con otro `eventId`
     -> `409` de idempotencia;
   - `idempotencyKey` de 101 caracteres -> `400`; `idempotencyKey` vacía (`""`) -> `201` como sin clave;
   - precedencia: header inválido + evento lleno -> `400`; evento inexistente + clave repetida ->
     `404`; evento lleno + clave repetida -> `409` de idempotencia;
   - reservar sin `X-User-Id` y con header no-UUID -> `400` `bad_request`, sin crear reserva;
   - `POST /api/events` con `capacity: 0`, `name` vacío o JSON malformado -> `400`;
   - `GET /api/bookings/me` devuelve solo las reservas de ese usuario;
   - tras toda la batería, ningún evento tiene `sold > capacity` ni `sold < 0`.
4. Persistencia entre reinicios (RF14): `docker compose restart booking`, esperar `Up` y comprobar que
   `GET /api/events` y `GET /api/bookings/me` devuelven los mismos datos, y que repetir una
   `idempotencyKey` usada antes del reinicio sigue devolviendo `409`.
5. Concurrencia real (RF15): crear un evento con `capacity: 3` y lanzar 10 altas en paralelo (mismo
   evento, claves distintas) -> exactamente 3 respuestas `201` y 7 `409` de aforo; `GET /api/events`
   devuelve `sold: 3`; ningún evento con `sold > capacity`.
6. Con Kafka parado (`docker compose stop kafka`), los checks manuales del punto 3 siguen en verde
   (RF16).
7. Sin rastro de Kafka, SSE ni AuthFilter en el servicio, con comandos concretos y salida esperada
   vacía:
   - `mvn -pl booking-service dependency:tree | grep -i kafka` -> sin líneas.
   - `grep -RIn -E "@KafkaListener|KafkaTemplate|org\.springframework\.kafka|spring-kafka" booking-service` -> sin resultados.
   - `grep -RIn -E "SseEmitter|AuthFilter|JwtParser|io\.jsonwebtoken" booking-service` -> sin resultados.
8. RNF4: `grep -RIn -E "JWT_SECRET|secret" booking-service` -> sin resultados (este servicio no maneja
   secretos) y en `booking-service/src/main/resources/application.yml` la única credencial es la de
   desarrollo `eventpay`, idéntica a la del compose.
9. El test arquitectónico de pureza del dominio está en verde (RNF2).
10. Commit pequeño en español citando la spec: `d2-booking-sync (specs/002-d2-booking-dominio-sync)`.

## 12. Dudas abiertas

Ninguna. Las cinco dudas iniciales se cerraron el 23 de octubre de 2026 y los 16 hallazgos de la
revisión 2 están resueltos en §2 (dependencias, desviación de calendario), §4, §5 (precedencia y
mensajes literales), §6, §8, §9 (sin MapStruct, Dockerfiles, `spring-boot-maven-plugin`), §10 y §11.

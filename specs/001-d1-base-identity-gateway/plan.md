# 001 — Plan de implementación (D1)

Deriva de `spec.md`. Requisitos funcionales usados abajo (extraídos de §3):
RF1 registro 201 + 400 por email inválido o password < 8 · RF2 duplicado 409 con email en minúsculas ·
RF3 login 200 con JWT (`sub`, 1h) / 401 único · RF4 formato de error `{error, message}` + 400 en body malformado ·
RF5 BCrypt + secreto por entorno, nada en claro ni en logs · RF6 gateway `/auth/**` sin reescritura + clientes Eureka ·
RF7 transversales: unicidad ante concurrencia, 502/503 si identity cae, expiración cubierta por test.

## 1. Archivos y responsabilidad (RF que cubre cada uno)

Crear:
- `identity-service/pom.xml` — starters del stack + JJWT `0.12.6` + Mockito en scope test (RF1–RF5).
- `identity-service/.../IdentityApplication.java` — arranque Boot (todos).
- `identity-service/.../web/dto/RegisterRequest.java` — `{email, password}` con `@Email` y longitud mínima 8 (RF1).
- `identity-service/.../web/dto/LoginRequest.java` — `{email, password}` (RF3).
- `identity-service/.../web/dto/RegisterResponse.java` — `{userId, email}` (RF1).
- `identity-service/.../web/dto/LoginResponse.java` — `{token}` (RF3).
- `identity-service/.../web/AuthController.java` — `POST /auth/register` -> 201 y `POST /auth/login` -> 200; delega todo al servicio, sin lógica (RF1, RF3).
- `identity-service/.../web/GlobalExceptionHandler.java` — mapea validación/duplicado/credenciales al formato `{error, message}` con 400/409/401 (RF4; RF2 usa el 409).
- `identity-service/.../service/AuthService.java` — orquesta registro y login: normaliza email, verifica duplicado, BCrypt, delega firma (RF1, RF2, RF3, RF5).
- `identity-service/.../service/JwtService.java` — firma HS256 con `sub` y expiración 1h usando el secreto de `JWT_SECRET` y el instante recibido por parámetro (RF3, RF5).
- `identity-service/.../service/CredentialsException.java` — error interno de credenciales inválidas, traducido a 401 único fuera del servicio (RF3).
- `identity-service/.../persistence/UserEntity.java` — tabla `users(id UUID, email unique, pass_hash)` (RF1, RF2, RF5).
- `identity-service/.../persistence/UserRepository.java` — `findByEmail`, `existsByEmail` (RF2, RF3).
- `identity-service/src/main/resources/application.yml` — puerto 8081, datasource por `DB_HOST`/`DB_PORT` (`postgres-users:5432` en docker, `localhost:5433` en local), `ddl-auto: update`, secreto solo `JWT_SECRET` (RF5, RF6-infra).
- `identity-service/Dockerfile` — mismo patrón multistage que eureka (todos).
- `gateway-service/pom.xml`, `gateway-service/.../GatewayApplication.java` — arranque (RF6).
- `gateway-service/src/main/resources/application.yml` — ruta abierta `Path=/auth/**` -> identity sin filtros ni reescritura (RF6).
- `gateway-service/Dockerfile` — mismo patrón (RF6).
Modificar:
- `pom.xml` raíz — alta de `<module>identity-service</module>` y `<module>gateway-service</module>` (todos).
- `docker-compose.yml` — servicios `identity` (8081, env `JWT_SECRET`/`DB_HOST`, `depends_on: postgres-users`) y `gateway` (8080), ambos con `context: .` (RF5, RF6).

## 2. Funciones puras de lógica (con `now` como parámetro)

Sin estado, sin Spring, testeables sin contexto (RF entre paréntesis):
- `normalizeEmail(raw) -> lower(trim(raw))` (RF2: la comparación 409 siempre en minúsculas).
- `validateRegisterInput(email, password) -> lista de violaciones` (formato email, longitud >= 8) (RF1).
- `buildClaims(userId, now) -> {sub: userId, iat: now, exp: now + 1h}` (RF3: la expiración deriva del instante inyectado, no del reloj del sistema).
- `isExpired(claims, now) -> now >= exp` (RF7: soporte del test de expiración).
- `sameCredentialsError() -> 401 "Credenciales inválidas"` (RF3: mensaje único, sin distinguir causa).
- `toErrorBody(code, message) -> {error, message}` (RF4).
`JwtService` recibe `java.time.Clock` en constructor: en producción el reloj real, en tests uno fijo. Así "hoy/ahora" es parámetro y la expiración de 1h se verifica de forma determinista.

## 3. Algoritmos en pseudocódigo

No hay mapa en esta spec (§2 excluye todo lo visual): el algoritmo a fijar es el flujo de autenticación.
- Registro (cubre RF1, RF2, RF4, RF5):
  `email = normalizeEmail(input.email); violaciones = validateRegisterInput(email, input.password);`
  `si hay violaciones -> 400 toErrorBody("bad_request", ...);`
  `si repository.existsByEmail(email) -> 409 toErrorBody("conflict", "Email ya registrado");`
  `guardar User(id=uuid(), email, pass_hash=bcrypt(input.password));`
  `ante DataIntegrityViolation (carrera) -> 409 igual que arriba;`
  `responder 201 {userId, email}.`
- Login (cubre RF3, RF4, RF5):
  `user = repository.findByEmail(normalizeEmail(input.email));`
  `si user ausente o no bcrypt.matches(input.password, user.pass_hash) -> 401 toErrorBody("unauthorized", "Credenciales inválidas");`
  `token = jwtService.sign(user.id, now); responder 200 {token}.`
- Gateway (cubre RF6, RF7): `si path empieza por /auth/ -> reenviar a identity:8081 sin tocar el path;`
  `si identity no responde -> 502/503 por defecto, sin personalizar.`

## 4. Interfaz

N/A en D1 por decisión de la spec (§2: `No: Angular`). No se pinta nada: la "interfaz" de este día
son los JSON de §3 verificados con `.http`/curl contra `:8080` (RF1–RF4 observables por HTTP).
El front llega en la spec 005.

## 5. Decisiones técnicas y alternativa descartada

1. `ddl-auto: update` en D1 (RF1). Justificación: cero fricción para quien empieza; una sola línea frente a migraciones.
   Descartada: Flyway/Liquibase desde el día 1 (más piezas que mantener antes de tener ni un endpoint).
2. Sin reescritura de path en el gateway (RF6). Justificación: lo que entra (`/auth/login`) es lo que llega; depuración trivial.
   Descartada: `StripPrefix` + controlador en `/login` (capas de indirección sin beneficio en D1).
3. `401` único sin distinguir causa (RF3). Justificación: principio 5 (protección de datos) prohíbe enumerar usuarios.
   Descartada: `404` email inexistente + `401` password errónea (filtra qué emails existen).
4. Unicidad por constraint + chequeo previo, no solo chequeo (RF2/RF7). Justificación: el chequeo previo tiene carrera; el constraint es la garantía real.
   Descartada: solo `existsByEmail` antes de guardar.
5. `Clock` inyectado en vez de `Instant.now()` directo (RF3/RF7). Justificación: tests deterministas de la expiración 1h sin esperas.
   Descartada: leer el reloj del sistema dentro de `JwtService` (haría el test de expiración frágil o lento).
6. JJWT `0.12.6` fijado, no rango (principio 1). Justificación: reproducible para un principiante; los rangos cambian API entre parches.
   Descartada: `0.12.x` tal cual pedía el primer borrador de la spec.

## 6. Estrategia de tests (principio 4: sin dependencias nuevas)

- `AuthServiceTest` (Mockito, sin Spring): register ok (verifica BCrypt y guardado en minúsculas) [RF1, RF2, RF5],
  duplicado -> 409 [RF2], login ok (token con `sub` y `exp = now + 1h` con `Clock` fijo) [RF3],
  password errónea e email inexistente -> mismo 401 [RF3].
- `JwtServiceTest` (puro, sin Spring): firma y expiración exacta con `Clock` fijo [RF3, RF7].
- Contratos HTTP (§6 de la spec) como verificación manual: 201 / 409 (incl. mayúsculas) / 200 con token /
  401 idéntico / 400, más grep de que `JWT_SECRET` nunca aparece con valor (principio 5) [RF1–RF6].
- Todo dentro de `mvn verify`; ninguna librería de test fuera de las ya declaradas.

# 001 — D1 Base identity + gateway (Día 1)

- Estado: pendiente
- Enlaces: `docs/CONSTITUTION.md` (principios 1, 2, 4, 5) · `AGENTS.md` · `MEMORY.md §7`

## 1. Objetivo

Levantar el flujo de autenticación: registro/login JWT en `identity-service`
y ruta `/auth/**` en el gateway. Nada de booking todavía.

## 2. Alcance / No hacer

- Sí: `identity-service` layered (web/service/jpa), `gateway-service` con ruta `/auth/**`,
  alta de ambos módulos en el pom raíz y en `docker-compose.yml`.
- No: booking-service, Kafka (ni publishers, ni listeners, ni topics, ni menciones en contratos),
  AuthFilter JWT, Angular. No crear `booking-service` en este día.
- Dependencias permitidas (constitución, principio 4): solo starters del stack fijado
  (Boot 3.2.5 / Cloud 2023.0.1 BOM) + JJWT `0.12.6` + stack de tests. Cualquier otra requiere aprobación.
- Idioma (constitución, principio 6): identificadores en inglés; mensajes de error, docs y commit en español.

## 3. Contratos

- `POST /auth/register {email, password}` -> `201 {userId, email}` (`userId` UUID en texto).
  Errores: `400` email con formato inválido (`@Email`) o password de menos de 8 caracteres;
  `409` si el email ya existe (comparación en minúsculas: se guarda siempre en minúsculas).
- `POST /auth/login {email, password}` -> `200 {token}` (JWT HS256, `sub=userId`, expiración 1h).
  Error `401` con mensaje único `Credenciales inválidas` tanto si el email no existe
  como si la password falla (no revelar qué parte falló).
- Body JSON ausente o malformado -> `400` (comportamiento por defecto de Spring, sin personalizar).
- Formato de error común: `{error, message}` (p. ej. `{error: "conflict", message: "..."}`).
- Entidad `users(id UUID, email unique, pass_hash)`. Password con BCrypt, nunca en claro ni en logs.
- Gateway: ruta abierta `Path=/auth/**` (todos los métodos) -> `identity-service:8081`
  sin reescritura de path (identity expone `/auth/register` y `/auth/login` tal cual).
  Identity y gateway registrados como clientes Eureka.
- Concurrencia: dos registers simultáneos del mismo email -> uno gana con `201`, el otro `409`
  (garantizado por el constraint unique, no solo por chequeo previo).
- Gateway con identity caído -> `502/503` por defecto del gateway (sin personalizar en D1).
- Expiración del token: se firma a 1h y se cubre con test unitario del `JwtService`;
  su validación en tráfico real llega con el AuthFilter (D4).

## 4. Archivos

- Crear `identity-service/pom.xml` (hijo de `eventpay-parent`): `web, validation, data-jpa, postgresql, security, eureka-client`, JJWT `0.12.6`, Mockito en scope test.
- Crear `identity-service/src/main/java/com/eventpay/identity/`:
  `IdentityApplication.java`, `web/{AuthController, dto/RegisterRequest, dto/LoginRequest, dto/RegisterResponse, dto/LoginResponse}`,
  `service/{AuthService, JwtService}`, `persistence/{UserEntity, UserRepository}`, `web/GlobalExceptionHandler.java` (mapea 400/409/401 al formato común).
- Crear `identity-service/src/main/resources/application.yml` (puerto 8081,
  datasource por variable `DB_HOST`: `postgres-users` en docker, `localhost` en local;
  JPA `ddl-auto: update` en D1; JWT secret solo por variable `JWT_SECRET`, mínimo 32 caracteres,
  sin valor en el repo).
- Crear `identity-service/Dockerfile` (igual patrón que `eureka-server/Dockerfile`).
- Crear `gateway-service/pom.xml`: `gateway-reactive, eureka-client`.
- Crear `gateway-service/src/main/java/com/eventpay/gateway/GatewayApplication.java`.
- Crear `gateway-service/src/main/resources/application.yml` (puerto 8080, ruta `/auth/**` -> `identity-service`, sin filtros).
- Crear `gateway-service/Dockerfile`.
- Modificar `pom.xml` raíz: añadir `<module>identity-service</module>` y `<module>gateway-service</module>`.
- Modificar `docker-compose.yml`: descomentar `gateway` e `identity` (con `context: .` + `dockerfile`);
  `JWT_SECRET` y `DB_HOST` solo vía `environment`, con `depends_on` de identity a `postgres-users`
  más `restart: unless-stopped` en los servicios JVM.

## 5. Tests

- `AuthServiceTest` (Mockito, sin Spring), 1 clase con 4 casos: register ok, register duplicado -> 409,
  login ok devuelve token con `sub` y expiración 1h, login con pass errónea -> 401.
- Objetivo D1: esa clase en verde dentro de `mvn verify`.

## 6. Done verificable

- `mvn verify` en verde.
- `docker compose up --build -d` + `docker compose ps` con postgres x2 + kafka + eureka + identity + gateway Up,
  y los checks manuales de abajo en verde (eso prueba readiness, no solo `Up`).
- Manual: `POST localhost:8080/auth/register` -> 201; repetirlo (mayúsculas incluidas) -> 409;
  `POST localhost:8080/auth/login` -> 200 con `token`; login con pass mala o email inexistente -> 401 idéntico;
  body inválido o password corta -> 400.
- Secreto: `grep -ri jwt_secret --include=*.yml --include=*.java identity-service gateway-service` solo muestra
  la lectura de la variable, nunca un valor; ningún log imprime password ni token.
- Commit pequeño ES que cita la spec: `d1-identity (specs/001-d1-base-identity-gateway)`.

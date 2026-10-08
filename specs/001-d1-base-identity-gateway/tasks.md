# 001 — Tasks D1

RF: RF1 registro · RF2 duplicado 409 · RF3 login JWT/401 · RF4 formato error · RF5 BCrypt/secreto ·
RF6 gateway+Eureka · RF7 concurrencia/caída/expiración.

- [x] T1 Alta de módulos en el pom raíz (RF: todos).
  Hecho cuando: `pom.xml` lista `identity-service` y `gateway-service` y `mvn -N validate` pasa.
- [x] T2 `identity-service/pom.xml` con starters + JJWT `0.12.6` + Mockito en scope test (RF: todos).
  Hecho cuando: `mvn -pl identity-service dependency:resolve` descarga sin errores.
- [x] T3 Persistencia `UserEntity` + `UserRepository` + `application.yml` base (RF1, RF2, RF5).
  Hecho cuando: la app arranca en local contra `localhost:5433` y crea la tabla `users`.
- [x] T4 DTOs con validación (`RegisterRequest` `@Email` + mín. 8, `LoginRequest`, `RegisterResponse`, `LoginResponse`) (RF1, RF3).
  Hecho cuando: compilan y `POST` sin `@Email`/password corta devuelve 400.
- [x] T5 `JwtService` con `Clock` inyectado + `CredentialsException` (RF3, RF5, RF7).
  Hecho cuando: `JwtServiceTest` con reloj fijo verifica `sub` y `exp = now + 1h`.
- [x] T6 `AuthService` (minúsculas, BCrypt, 409 por constraint, 401 único) (RF1, RF2, RF3, RF5).
  Hecho cuando: `AuthServiceTest` (4 casos Mockito, sin Spring) en verde.
- [x] T7 `AuthController` + `GlobalExceptionHandler` al formato `{error, message}` (RF1, RF3, RF4).
  Hecho cuando: register/login responden 201/200/400/409/401 con ese formato.
- [x] T8 `gateway-service` + ruta abierta `/auth/**` sin reescritura (RF6).
  Hecho cuando: `POST localhost:8080/auth/login` llega a identity y responde.
- [x] T9 `Dockerfile`s + servicios en `docker-compose.yml` (`JWT_SECRET`/`DB_HOST` por env, `depends_on`, `restart`) (RF5, RF6).
  Hecho cuando: `docker compose config` válido y `up --build -d` deja 6 servicios Up.
- [x] T10 Batería manual + secreto + commit (RF1–RF7).
  Hecho cuando: 201 / 409 (incl. mayúsculas) / 200 con token / 401 idéntico / 400 en verde;
  el grep de `JWT_SECRET` no muestra valores; commit `d1-identity (specs/001-d1-base-identity-gateway)`.

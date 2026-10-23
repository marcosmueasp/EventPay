# 006 — D6 Testing + docs (Día 6)

- Estado: pendiente (requiere 001-005)
- Enlaces: `docs/CONSTITUTION.md §2, §5` · `AGENTS.md`

## 1. Objetivo

Cerrar la evidencia de la oferta: tests que faltan y README que permite la demo a un tercero.

## 2. Alcance / No hacer

- Sí: ArchUnit, `@WebMvcTest`, README demo, fichero `.http`, gif demo.
- No: nuevas funcionalidades, cambios de contratos, Angular nuevo. Solo rematar y documentar.

## 3. Contratos

- Batería final (5 tests verdes):
  1. `AuthServiceTest` (D1, Mockito).
  2. `BookingServiceTest` (D2, dominio sin Spring).
  3. Test EmbeddedKafka PENDING→PAID (D3).
  4. `@WebMvcTest` de `BookingController` (mock del use-case, 201/404/409).
  5. ArchUnit: `domain` no importa `org.springframework.*` ni `infrastructure`.
- README: diagrama del flujo, stack, `docker compose up --build`, demo 60s paso a paso, topics JSON,
  justificación del worker interno (adapter sustituible) y tabla oferta->evidencia.
- `.http`: requests `register`, `login`, `create event`, `create booking`, `list me` (con variable `token`).
- Gif demo de ≤60s (login->reservar->PAID en vivo).

## 4. Archivos

- Crear `booking-service/src/test/java/.../HexagonalArchitectureTest.java` (ArchUnit).
- Crear `booking-service/src/test/java/.../BookingControllerWebTest.java` (`@WebMvcTest`).
- Modificar `README.md` (sustituir el título vacío por la doc demo completa).
- Crear `docs/demo.http` (o `requests.http` en raíz; decidir una ruta y documentarla en README).
- Añadir `docs/demo.gif` (o enlace) + actualizar `MEMORY.md` con el estado final.

## 5. Tests

- Los 5 de §3, todos en `mvn verify`.

## 6. Done verificable

- `mvn verify` en verde con los 5 tests.
- Un tercero con solo README + `.http` reproduce la demo 60s.
- Commit pequeño ES `d6-tests-docs`.

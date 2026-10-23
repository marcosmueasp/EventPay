# 007 — D7 Buffer + release (Día 7)

- Estado: pendiente (requiere 006)
- Enlaces: `docs/CONSTITUTION.md §5` · `AGENTS.md`

## 1. Objetivo

Dejar el repo entregable: demo DLQ, CORS/perfiles y tag `v0.1-mvp`.

## 2. Alcance / No hacer

- Sí: guion demo DLQ, CORS mínimo, perfiles `local/docker`, limpieza y tag.
- No: nuevas features, nuevos topics, refactorings grandes. Día colchón, no día de inventar.

## 3. Contratos

- Demo DLQ reproducible: cómo publicar un mensaje corrupto en `payment.result`,
  dónde verlo (`payment.result.DLT`) y cómo comprobar que el listener sigue vivo.
- CORS: solo orígenes `http://localhost:4200` (local) y el del front dockerizado si existe.
  Nada de `*` en producción del tag.
- Perfiles: `local` (Kafka `localhost:29092`, Eureka `localhost:8761`, Postgres `localhost:5433/5434`)
  y `docker` (hosts de compose). Documentar `SPRING_PROFILES_ACTIVE` en README y compose.
- Release: tag `v0.1-mvp` sobre main con todo en verde.

## 4. Archivos

- Modificar `README.md`: sección demo DLQ + perfiles + CORS.
- Modificar `gateway-service` y `booking-service`: config CORS + `application-{local,docker}.yml` si aún comparten uno solo.
- Modificar `docker-compose.yml`: `SPRING_PROFILES_ACTIVE=docker` donde aplique.
- Actualizar `MEMORY.md` (cierre) y `AGENTS.md` solo si cambió algún comando.
- Crear tag `v0.1-mvp` (comando git, no archivo).

## 5. Tests

- `mvn verify` sigue verde. Sin tests nuevos salvo que un perfil rompa alguno existente.

## 6. Done verificable

- `docker compose up --build` levanta todo en <2min desde cero (`docker compose down -v` previo).
- Demo 60s login->reservar->PAID en vivo + demo DLQ con mensaje corrupto.
- Tag `v0.1-mvp` creado (`git tag v0.1-mvp && git push origin v0.1-mvp`).
- Commit pequeño ES `d7-release`.

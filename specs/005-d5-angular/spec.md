# 005 — D5 Angular 18 (Día 5)

- Estado: pendiente (requiere 004: `/auth/**`, `/api/**` con JWT y SSE funcionando)
- Enlaces: `docs/CONSTITUTION.md §5` · `AGENTS.md`

## 1. Objetivo

Front mínimo que demuestra la demo 60s: login, ver eventos, reservar y ver PENDING pasar a PAID en vivo por SSE.

## 2. Alcance / No hacer

- Sí: Angular 18 standalone (3 componentes/páginas), interceptor Bearer, SSE, estilos mínimos + vista móvil.
- No: llamar directo a `:8081`/`:8082` (todo por Gateway `:8080`), gestión de usuarios/edición de eventos,
  paginación, i18n, librerías UI pesadas. Solo lo necesario para la demo.

## 3. Contratos

- Base URL: `http://localhost:8080`. Prohibido hardcodear `:8081`/`:8082` en el front.
- Login: `POST /auth/login` guarda `token` en Local Storage clave `EventPay`.
  Interceptor añade `Authorization: Bearer <token>` a toda llamada `/api/**`.
- Páginas (standalone, 3): `login`, `events` (`GET /api/events`, `POST /api/events`),
  `bookings` (`POST /api/bookings` con `Idempotency-Key` generada en cliente, `GET /api/bookings/me`,
  suscripción SSE a `GET /api/bookings/{id}/stream` que actualiza el estado sin refrescar).
- SSE: `EventSource` con token (query o header según implemente el gateway en D4; si D4 solo acepta header,
  usar `fetch` + stream en lugar de `EventSource` nativo). Reintento simple al perder conexión.

## 4. Archivos

- Crear `frontend/` (Angular 18): `package.json`, `angular.json`, `src/{main.ts, app/app.{component,routes}.ts}`.
- Crear `src/app/core/{auth.service.ts, auth.interceptor.ts, api.service.ts, sse.service.ts}`.
- Crear `src/app/pages/{login, events, bookings}/*` (3 componentes standalone + templates + estilos).
- Crear `src/environments/{environment.ts, environment.docker.ts}` (apiBase `http://localhost:8080`).
- No tocar backend salvo CORS si la demo lo exige (y entonces documentarlo aquí).

## 5. Tests

- Sin tests automáticos de front obligatorios. Verificación manual según §6.

## 6. Done verificable

- `npm install && ng serve` (puerto 4200) sin errores de consola.
- Demo 60s en Chrome: login -> reservar -> PENDING pasa a PAID en vivo por SSE.
- Vista móvil sin cortes (responsive básico).
- Para empezar de cero: DevTools -> Application -> Local Storage -> borrar la clave `EventPay`.
- Commit pequeño ES `d5-angular`.

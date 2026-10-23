# EventPay — Constitución
1. Stack fijo: Java 17, Boot 3.2.5, Cloud 2023.0.1, Kafka 3.7, Postgres 16, Angular 18. Se verifica en `pom.xml` y `docker-compose.yml`; nada fuera de esto sin aprobación.
2. La spec manda: ningún código fuera de `specs/NNN-*/spec.md` + `tasks.md`; cada cambio cita su spec.
3. Lógica separada de interfaz: `domain/` sin Spring y el front solo habla con el Gateway `:8080`.
4. Tests sin dependencias nuevas: `mvn verify` verde con lo ya declarado (JUnit5, Mockito, EmbeddedKafka, ArchUnit).
5. Datos del usuario protegidos: BCrypt, JWT 1h, secretos por entorno; nunca contraseñas en claro ni en logs.
6. Idioma: código en inglés; docs, commits y textos de usuario en español.

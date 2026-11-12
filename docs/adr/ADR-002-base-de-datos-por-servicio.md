# ADR-002: Una base de datos por servicio (persistencia políglota)

- Estado: Aceptada
- Fecha: 2026-09-29

## Contexto
Al separar los servicios (ADR-001) había que decidir dónde guarda cada uno sus datos. Las fuerzas en juego:
- Investigadores necesita unicidad del correo, transacciones (el Outbox de ADR-004 va en la misma
  transacción) y un esquema estable versionado.
- Las publicaciones tienen campos distintos según el tipo (artículo, libro, ponencia…), guardados en un
  mapa `detalles`. Su consulta principal es "publicaciones de un investigador en un año"
  (`countByInvestigadorCorreoAndAnio`).
- Si dos servicios comparten tablas, quedan acoplados por el esquema y ya no se pueden desplegar por separado.

## Decisión
- `investigadores-service` → **PostgreSQL 16** (base `rica`). El esquema se gestiona con migraciones
  Flyway (`V1` a `V3`) y `ddl-auto: validate`.
- `publicaciones-service` → **MongoDB 7** (base `rica`, colección `publicaciones`).
- `consultas-service` → **MongoDB 7**, en otra base (`rica_consultas`), ver ADR-006.

Ningún servicio lee la base de otro: los datos ajenos se obtienen por gRPC (ADR-003) o por eventos (ADR-004).

## Alternativas descartadas
- **Una sola base PostgreSQL compartida.** Habría permitido JOINs y transacciones locales, pero se
  descartó porque convierte el esquema en un contrato implícito entre servicios (antipatrón de base
  compartida).
- **PostgreSQL para todo, con `jsonb` para los `detalles`.** Habría dejado un solo motor que operar. Se
  descartó para practicar persistencia políglota. Reconocemos que técnicamente habría bastado.
- **Redis como caché de fichas** (taller 11). Se descartó porque la ficha ya es una lectura por clave sobre
  una proyección.

## Consecuencias
+ Cada servicio cambia su esquema sin coordinar con los demás.
+ Las publicaciones admiten campos nuevos por tipo sin migraciones.
- Dos motores que conocer, respaldar y monitorear.
- MongoDB no garantiza aquí la unicidad ni la integridad referencial con investigadores: la validación
  depende de la llamada gRPC en el momento del registro.
- En el entorno actual, publicaciones y consultas comparten un solo contenedor de MongoDB (en bases
  distintas): si ese contenedor cae, caen los dos (riesgo R4).

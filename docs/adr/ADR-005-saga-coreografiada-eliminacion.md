# ADR-005: Eliminar investigadores con una Saga coreografiada

- Estado: Aceptada
- Fecha: 2026-10-11

## Contexto
Al eliminar un investigador, sus publicaciones (en otra base, en otro servicio) deben quedar marcadas como
huérfanas. No hay transacción que abarque PostgreSQL y MongoDB a la vez. Además, el investigador no debe
desaparecer antes de que publicaciones haya hecho su parte.

## Decisión
Una Saga **coreografiada**, sin coordinador central, en la que cada participante reacciona a un evento:
1. `DELETE /api/investigadores/{id}` → investigadores marca al investigador `EN_ELIMINACION` y publica
   `investigadores.en-eliminacion` (vía Outbox). Si ya estaba en eliminación, no hace nada (idempotente).
2. publicaciones marca sus publicaciones como huérfanas y publica `publicaciones.marcadas-huerfanas`.
3. investigadores recibe la confirmación, borra al investigador y publica `investigadores.eliminados`.
4. consultas actualiza la ficha en los pasos 1 y 3.

## Alternativas descartadas
- **Commit en dos fases (2PC/XA) entre PostgreSQL y MongoDB.** Se descartó porque MongoDB no participa en
  XA con PostgreSQL y bloquea recursos mientras espera.
- **Saga orquestada** (un coordinador que persiste el estado de cada paso). Da más visibilidad y facilita
  las compensaciones. Se descartó porque con solo dos participantes y un camino feliz, el coordinador sería
  otro componente que operar. Si la Saga crece a más pasos, cambiaríamos a orquestación (ver
  `evidencias/taller-observabilidad/5-discusion.md`).
- **Borrado en cascada síncrono** (investigadores llama a publicaciones por gRPC antes de borrar). Se
  descartó porque una caída de publicaciones haría fallar la eliminación.

## Consecuencias
+ No hay transacción distribuida y cada servicio sigue siendo dueño de sus datos.
+ La Saga tolera que publicaciones esté caído: al volver, procesa el evento pendiente (se verificó en el
  taller 10, con lag 0 → 1 → 0).
- Consistencia eventual: durante la Saga, el investigador existe en estado `EN_ELIMINACION`.
- **No hay compensación implementada:** si publicaciones falla de forma permanente, el investigador queda
  atascado (riesgo R2). La compensación y la alerta están diseñadas pero no construidas.
- La confirmación del paso 2 se envía sin Outbox (riesgo R1, ADR-007).
- El flujo completo no se ve en un solo lugar del código: hay que leer tres servicios.

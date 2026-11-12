# ADR-007: Usar Transactional Outbox también en publicaciones-service

- Estado: **Propuesta** (no implementada)
- Fecha: 2026-11-11

## Contexto
publicaciones-service publica `publicaciones.registradas` y `publicaciones.marcadas-huerfanas` con
`kafkaTemplate.send(...)` **después** de guardar en MongoDB, sin Outbox. Es la misma escritura dual que
ADR-004 resolvió en investigadores. Si el proceso cae entre el `save` y el `send`, o si Kafka rechaza el
envío:
- la ficha en consultas-service se queda sin esa publicación, o
- la Saga de eliminación (ADR-005) queda atascada, porque investigadores nunca recibe la confirmación.

## Decisión propuesta
Guardar el evento en una colección `outbox` de la misma base MongoDB, dentro de una transacción
multi-documento junto con la publicación, y publicarlo con un poller análogo a `OutboxPoller` de
investigadores-service.

## Alternativas consideradas
- **Dejarlo como está y reprocesar a mano.** Es aceptable mientras RICA sea un proyecto de curso, pero el
  error es silencioso.
- **Producer transaccional de Kafka.** No resuelve el problema: la transacción de Kafka no incluye la
  escritura en MongoDB.
- **Change Streams de MongoDB.** Evita el poller, pero exige que MongoDB corra como replica set, y hoy es
  un nodo único.

## Consecuencias esperadas
+ Ningún evento de publicaciones se perdería y se eliminaría la causa principal de una Saga atascada.
- Las transacciones multi-documento exigen convertir el contenedor de MongoDB en un replica set de un nodo.
- Otro poller y otra colección que operar, y una latencia adicional de hasta unos 2 s.

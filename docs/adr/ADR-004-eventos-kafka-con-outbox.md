# ADR-004: Propagar hechos con eventos en Kafka publicados mediante Transactional Outbox

- Estado: Aceptada (en investigadores-service; para publicaciones-service ver ADR-007)
- Fecha: 2026-10-11

## Contexto
Varios servicios necesitan enterarse de hechos que ocurren en otro: que se registró un investigador, que
se está eliminando o que ya se eliminó. Son **propagaciones**, no consultas: el productor no necesita
esperar a nadie. Además, investigadores-service guarda en PostgreSQL y debe publicar en Kafka. Hacer ambas
cosas por separado deja el sistema inconsistente si el proceso cae entre las dos (escritura dual).

## Decisión
- Comunicación asíncrona por **Kafka**, con tópicos nombrados en pasado (`investigadores.registrados`,
  `investigadores.en-eliminacion`, `investigadores.eliminados`), clave = correo institucional y cuerpo JSON
  que incluye el `correlationId`.
- En investigadores-service, `OutboxPublicadorEventos` guarda el evento en la tabla outbox **en la misma
  transacción** que el cambio de negocio. `OutboxPoller` (`@Scheduled`, cada 2 s) lo publica en Kafka y lo
  marca como enviado.

## Alternativas descartadas
- **Enviar a Kafka directamente después del `save`.** Es más simple, pero pierde el evento si el proceso
  cae entre ambos pasos o si Kafka está caído. Es justo lo que hoy hace publicaciones-service (riesgo R1).
- **Change Data Capture con Debezium sobre el outbox.** Elimina el polling, pero agrega Kafka Connect que
  operar: es desproporcionado para el curso.
- **Llamadas REST/gRPC a cada interesado.** El productor tendría que conocer y esperar a cada consumidor,
  y una caída de consultas bloquearía el registro.

## Consecuencias
+ No se pierden eventos aunque Kafka esté caído: se acumulan en la tabla y salen cuando vuelve.
+ Se conserva el orden por investigador (misma clave, misma partición).
+ Los productores no conocen a sus consumidores: consultas-service se agregó sin tocar investigadores.
- Entrega al menos una vez: los consumidores reciben duplicados y deben ser idempotentes (ADR-006).
- Latencia de hasta unos 2 s por el intervalo de sondeo.
- Hay una tabla y un proceso de sondeo que operar; la tabla crece si no se purga (no hay purga implementada).
- No hay orden entre tópicos distintos: una publicación puede llegar a consultas antes que su investigador.

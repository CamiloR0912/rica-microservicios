# ADR-006: Separar las lecturas en consultas-service (CQRS) con consumidores robustos

- Estado: Aceptada
- Fecha: 2026-11-02 (consumidores robustos: 2026-11-08)

## Contexto
La ficha de un investigador, el ranking y las estadísticas combinan datos de investigadores (PostgreSQL)
y de publicaciones (MongoDB). Armarlos en cada petición exigiría dos llamadas síncronas encadenadas y haría
que la consulta dependiera de la disponibilidad de ambos servicios. Además, las lecturas son mucho más
frecuentes que las escrituras.

## Decisión
- Un servicio de solo lectura, `consultas-service`, con su propia base MongoDB (`rica_consultas`). Mantiene
  un documento `FichaInvestigador` desnormalizado (datos del investigador más el resumen de sus
  publicaciones), actualizado por los eventos de ADR-004.
- Endpoints de lectura: `/api/consultas/investigadores`, `/investigadores/{correo}`, `/ranking` y `/estadisticas`.
- Consumidores robustos (taller 9):
  - reintentos con espera exponencial (1 s, 2 s, 4 s);
  - **Dead Letter Topic** `<tópico>.DLT` para mensajes que no se pueden procesar;
  - **Idempotent Consumer**: colección `eventos_procesados` para las estadísticas y deduplicación por id de
    publicación en la ficha.

## Alternativas descartadas
- **API Composition en el gateway o en un BFF** (llamar a los dos servicios y combinar). No requiere
  estado nuevo, pero acopla la disponibilidad de la consulta a ambos servicios y hace N+1 llamadas para el ranking.
- **Leer directamente las bases de los otros servicios.** Rompe ADR-002.
- **Vistas materializadas en PostgreSQL.** No sirve: los datos de publicaciones viven en MongoDB.

## Consecuencias
+ La ficha se obtiene con una sola lectura por clave y sigue disponible aunque investigadores o
  publicaciones estén caídos.
+ El modelo de lectura se puede reconstruir desde cero reprocesando los tópicos con otro `group-id`.
- Consistencia eventual: una publicación recién creada tarda en aparecer en la ficha (sondeo del Outbox
  más lag del consumidor).
- Los eventos de tópicos distintos llegan sin orden garantizado: el proyector debe crear fichas "vacías"
  que se completan después.
- `ProyectorFichas` usa `synchronized`, que solo es correcto con una instancia (riesgo R5).
- Los mensajes que terminan en la DLT no se reprocesan solos: hoy se revisan a mano.

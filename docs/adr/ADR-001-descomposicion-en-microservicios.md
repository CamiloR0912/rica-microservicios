# ADR-001: Descomponer el monolito en servicios por contexto acotado

- Estado: Aceptada
- Fecha: 2026-09-29

## Contexto
RICA nació como un monolito (`rica-api`) que manejaba investigadores y publicaciones en una sola
aplicación y una sola base. En el análisis de DDD aparecieron dos contextos con reglas y ritmos de cambio
distintos:
- **Investigadores:** identidad (correo institucional único) y ciclo de vida (activo, en eliminación).
- **Publicaciones:** registro con datos variables por tipo y un límite de 5 publicaciones por año.

Más adelante surgió una tercera necesidad, la de consultas que combinan ambos (ficha, ranking,
estadísticas). Las fuerzas en juego: el curso pide practicar comunicación entre servicios, el equipo es
pequeño y no hay Kubernetes.

## Decisión
Dividir el sistema en tres servicios desplegables por separado, uno por contexto:
`investigadores-service`, `publicaciones-service` y `consultas-service` (este último en ADR-006).
Delante de ellos van un `gateway` y un `eureka-server`.

## Alternativas descartadas
- **Monolito modular** (un solo desplegable con módulos `investigadores` y `publicaciones` y límites
  verificados con ArchUnit). Era la opción más barata de operar y suficiente para la carga real de RICA.
  Se descartó porque el objetivo del proyecto es practicar la integración entre servicios (gRPC, eventos,
  Saga). Desde el punto de vista de la carga, es una decisión discutible y la dejamos registrada como tal.
- **Un servicio por entidad** (además autores, revistas, grupos…). Se descartó por granularidad excesiva:
  habría generado cadenas síncronas largas.

## Consecuencias
+ Cada servicio tiene su propia base y su propio modelo (ADR-002) y se prueba por separado en el CI.
+ Una caída de publicaciones no impide registrar investigadores.
- Operar 5 procesos más PostgreSQL, MongoDB y Kafka en lugar de una aplicación y una base.
- Lo que antes era un `JOIN` o una transacción local ahora exige gRPC (ADR-003), eventos (ADR-004) y una
  Saga (ADR-005), con la consistencia eventual que eso implica.
- Depurar exige correlacionar logs de varios servicios (de ahí el `X-Correlation-Id` del taller 10).

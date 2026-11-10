# Discusión sobre una Saga atascada

## 1. Error permanente en publicaciones

El investigador quedaría en `EN_ELIMINACION`, porque `investigadores-service` solo lo borra cuando recibe la confirmación de publicaciones. Un usuario podría advertirlo al consultar las fichas, pero el equipo responsable debería recibir una alerta por la antigüedad de la Saga; el lag por sí solo no basta, pues un mensaje descartado después de los reintentos puede dejar el lag en cero sin completar la operación.

## 2. Compensación propuesta

Publicaría `publicaciones.marcado-huerfanas-fallido` con `sagaId`, `correoInstitucional`, `correlationId` y la causa, para que `investigadores-service` cancele esa eliminación, devuelva al investigador a `ACTIVO` y emita `investigadores.eliminacion-cancelada`, que actualizaría también la ficha en consultas. La transición sería idempotente al registrar el `sagaId` y aceptar la cancelación solo si esa misma Saga continúa pendiente, ignorando duplicados y confirmaciones tardías. Si algunas publicaciones ya quedaron huérfanas, primero habría que restaurar las afectadas por esa Saga y confirmar la restauración; hasta entonces mantendría la compensación pendiente para evitar declarar `ACTIVO` un estado inconsistente.

## 3. Métrica y alerta

Guardaría `eliminacionIniciadaEn` al comenzar la Saga y expondría un gauge `rica.sagas.eliminacion.atascadas` con la cantidad de investigadores que llevan más de diez minutos en `EN_ELIMINACION`. Activaría una alerta cuando el valor sea mayor que cero durante un minuto, dirigida al equipo responsable y acompañada por los ids de las Sagas en logs, sin usar correos ni ids como etiquetas de la métrica. Revisaría además el lag y los errores de los consumidores para distinguir entre un servicio detenido y un fallo de procesamiento.

## 4. Cuatro pasos adicionales

Con cuatro pasos más elegiría una Saga orquestada: un coordinador persistiría el estado de cada paso y decidiría los tiempos de espera, los reintentos y el orden de las compensaciones. La coreografía actual es manejable por su tamaño, pero al crecer sería más difícil reconstruir qué participante debe actuar y quién debe cancelar la operación; el orquestador también tendría que tolerar reinicios y mensajes duplicados.

Estas respuestas describen un diseño propuesto. El taller implementa observabilidad; no se han añadido el coordinador, la compensación, el campo de antigüedad ni la alerta.

# Brecha de trazabilidad en consultas

En el código original, `ProyeccionListener` entregaba el JSON directamente a `ProyectorFichas`, sin leer `correlationId`. Aunque el evento ya incluyera ese campo, el hilo consumidor de consultas no tendría el MDC de la petición HTTP y sus logs mostrarían `[consultas-service,]`.

Se cerró la brecha leyendo el id del mensaje antes de llamar al proyector y usando `MDC.putCloseable` dentro de un bloque `try-with-resources`. Los cuatro métodos del listener aplican ese mismo tratamiento y usan `sin-correlacion` para eventos antiguos que no traen el campo; al salir, incluso por una excepción, se elimina la clave del MDC para no contaminar el siguiente mensaje.

`MDC.putCloseable` elimina la clave al cerrar; no restaura un valor anterior, a diferencia de lo que afirma el texto del taller. Aquí el alcance corresponde al procesamiento de un evento en el hilo consumidor.

También se agregó un log al inicio de `InvestigadorService.iniciarEliminacion`, porque el código original no escribía la línea de inicio mostrada en el ejemplo del paso 6. El id se copia al JSON cuando se guarda el Outbox, de modo que sobrevive al cambio de hilo del poller y a las pausas de los consumidores.

El recorrido verificable es:

```text
DELETE con X-Correlation-Id
  -> filtro HTTP y MDC en investigadores
  -> Outbox: investigadores.en-eliminacion con correlationId
  -> publicaciones: MDC, publicaciones huérfanas y confirmación con el mismo id
  -> investigadores: MDC, eliminación y Outbox investigadores.eliminados
  -> consultas: MDC al proyectar EN_ELIMINACION y al borrar la ficha
```

Kafka conserva el orden dentro de una partición, pero los eventos de distintos tópicos pueden verse intercalados en los logs. El id permite relacionarlos; no implica un orden global entre los tres servicios.

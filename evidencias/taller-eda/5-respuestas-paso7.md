# Taller Lección 9: respuestas del paso 7 y explicación final

## Paso 7: ¿qué hacemos con la DLT?

**1. ¿Quién debería enterarse cuando llega un mensaje a la DLT y cómo?**
El equipo dueño de `consultas-service`, con una alerta automática que se dispare cuando la DLT reciba mensajes nuevos (por ejemplo, cuando su offset final crece). Un tablero con el total acumulado y la excepción más frecuente sirve para ver tendencias; un correo por cada mensaje generaría ruido si llegan muchos.

**2. ¿Cómo se reprocesaría un mensaje después de corregir el error?**
Con una herramienta aparte que muestre el mensaje, lo reenvíe al tópico original y deje registro de quién lo hizo, porque las cabeceras `kafka_dlt-*` indican su origen. Reenviarlo exige que el consumidor sea idempotente y, si se usa la posición como identificador, hay que considerar que el mensaje reenviado recibe un offset nuevo y por eso no se detecta como repetido.

**3. ¿Cuándo es válido descartar un mensaje de la DLT, y quién lo decide?**
Es válido cuando se comprueba que no representa un hecho de negocio, como el texto `esto no es json` de esta prueba, o cuando el hecho ya se aplicó por otra vía. Lo decide el dueño del servicio junto con el dueño del dato, y la decisión debe quedar registrada con el motivo.

**4. ¿Qué pasa si el mensaje de la DLT es válido pero llegó a destiempo?**
Reprocesarlo tarde puede aplicar un hecho viejo sobre un estado más nuevo; por ejemplo, un alta reenviada después de la eliminación de ese investigador recrearía su ficha. Antes de reenviar hay que comparar el evento con el estado actual, usando su timestamp original o una versión del agregado, y descartarlo si ya quedó superado.

**Conexión con la Lección 10 (observabilidad).** La DLT guarda el mensaje, pero solo es útil si alguien se entera. Las métricas de tamaño de la DLT y de lag de los consumidores alimentan las alertas, y los logs estructurados y las trazas permiten seguir un mensaje fallido desde el productor hasta la excepción que lo envió a la DLT.

## Entregable: reintentar un mensaje venenoso sin fin

Reintentar sin fin es peor que perderlo porque el mensaje nunca se va a arreglar y, mientras tanto, detiene la partición y bloquea todos los mensajes válidos que vienen detrás, de modo que un solo dato malo afecta a todo el flujo en lugar de perder solo ese dato. Es peor que enviarlo a una DLT porque la DLT consigue lo mejor de ambos casos: el consumidor sigue de inmediato y el mensaje queda guardado con su diagnóstico para revisarlo y reprocesarlo.

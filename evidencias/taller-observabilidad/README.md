# Taller de observabilidad en RICA

Se agregó observabilidad a la Saga de eliminación de investigadores y se verificó su recuperación después de detener un participante. Esta carpeta contiene respuestas HTTP, extractos de logs, mediciones de Kafka y las respuestas de discusión del taller de la lección 10.

## Cambios realizados

- Actuator en investigadores, publicaciones y consultas, con `health`, `info` y `metrics` expuestos y detalles de salud visibles.
- Patrón de log con nombre del servicio y `correlationId`.
- `CorrelationIdFilter` en investigadores: recibe `X-Correlation-Id` o genera un UUID, devuelve la cabecera y limpia el MDC al terminar.
- Persistencia del id en el JSON del Outbox y propagación durante la confirmación de publicaciones.
- Correlación en los cuatro métodos de `ProyeccionListener` y log del inicio de la eliminación.
- Cabecera `X-Correlation-Id: demo-001` en la petición de eliminación de Bruno.
- Pruebas de propagación, compatibilidad con eventos sin id y limpieza del MDC cuando ocurre un error.

En Spring Boot 4.1.1, el POM de `spring-boot-starter-actuator` ya incluye `spring-boot-starter-micrometer-metrics` como dependencia transitiva. Los endpoints de métricas funcionaron sin agregarla otra vez; la [documentación de métricas de Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) explica los medidores HTTP, JVM y de conexiones.

## Evidencias y resultados

Las capturas corresponden a una ejecución local del 10 de noviembre de 2026, zona `America/Bogota`. Los JSON y TXT son salidas del sistema; las explicaciones están en los archivos Markdown.

| Requisito | Archivo | Resultado observado |
| --- | --- | --- |
| Línea base del paso 2 | `0-linea-base-*.log`, `0-health-*.txt`, `0-eliminacion-sin-correlacion.json` | La Saga termina, pero no devuelve id ni lo muestra en los logs; los tres endpoints de salud responden 404 |
| Salud de investigadores | `1-health-investigadores.json` | `UP`, incluido PostgreSQL en `components.db` |
| Salud de publicaciones | `1-health-publicaciones.json` | `UP`, incluido MongoDB |
| Salud de consultas | `1-health-consultas.json` | `UP`, incluido MongoDB |
| Id elegido y generado | `2-cabecera-elegida.json`, `2-cabecera-generada.json` | Se conserva `demo-001` a través del gateway y se genera un UUID sin cabecera |
| Saga completa | `2-traza-*.log` | `demo-001` aparece en los tres servicios; se marcan dos publicaciones huérfanas |
| Eliminación confirmada | `2-investigador-despues.txt`, `2-ficha-despues.txt` | Ambos endpoints responden 404 |
| Brecha en consultas | `2-brecha-trazabilidad.md` | Explicación del problema y del cambio aplicado |
| Métricas HTTP | `3-http.server.requests.json`, `3-errores-404.json` | Conteo, duración y filtro de respuestas 404 |
| JVM y conexiones | `3-jvm.memory.used.json`, `3-hikaricp.connections.active.json` | Métricas consultadas correctamente |
| Lag de consultas | `3-lag-consultas-antes.txt`, `3-lag-consultas-detenido.txt`, `3-lag-consultas-recuperado.txt` | 0 → 3 → 0 en `investigadores.registrados` |
| Saga detenida | `4-fichas-en-eliminacion.json` | Investigador pendiente con una publicación |
| Lag de publicaciones | `4-lag-publicaciones-antes.txt`, `4-lag-publicaciones-detenido.txt`, `4-lag-publicaciones-recuperado.txt` | 0 → 1 → 0 en `investigadores.en-eliminacion` |
| Recuperación | `4-traza-*.log`, `4-ficha-despues.txt`, `4-fichas-pendientes-despues.json` | El id `demo-002` sobrevive a la pausa; la ficha devuelve 404 y no quedan fichas pendientes |
| Discusión | `5-discusion.md` | Cuatro respuestas y diseño de compensación |
| Pruebas automatizadas | `6-pruebas-maven.txt` | 40 pruebas aprobadas, sin fallos ni errores |

La captura HTTP registra `COUNT = 16`, `TOTAL_TIME = 0.6702979 s` y `MAX = 0.1820007 s`. `COUNT` es acumulado: para obtener una tasa hay que calcular su incremento entre dos lecturas y dividirlo por el tiempo transcurrido; el filtro `status:404` permite contar esos errores y `TOTAL_TIME / COUNT` aproxima la duración media de las peticiones incluidas.

El lag es la diferencia entre el offset final de la partición y el offset confirmado por el grupo. El endpoint de salud puede estar `UP` durante una Saga detenida, porque la salud de un proceso no demuestra que una operación distribuida haya terminado.

La línea base se capturó después de las pruebas de observabilidad, ejecutando una copia temporal del código original de la revisión `dab43d51532ca4701bfc307212b19ca9fdff3aa1`. Esto permitió comparar ambas versiones sin revertir archivos de trabajo: las capturas `0-*` pertenecen al código original y las capturas `1-*` a `4-*` a la solución.

## Cómo repetir la ejecución

Desde la raíz del repositorio, levanta la infraestructura:

```powershell
docker compose -f investigadores-service/docker-compose.yml up -d
docker compose -f publicaciones-service/docker-compose.yml up -d
```

Abre cinco terminales y ejecuta `./mvnw.cmd spring-boot:run` dentro de cada carpeta, en este orden: `eureka-server`, `investigadores-service`, `publicaciones-service`, `consultas-service` y `gateway`. Si Maven informa que falta `JAVA_HOME`, asígnalo a tu instalación del JDK 25 en cada terminal antes de ejecutar el wrapper.

Para verificar las pruebas desde la raíz:

```powershell
./investigadores-service/mvnw.cmd -f investigadores-service/pom.xml test
./publicaciones-service/mvnw.cmd -f publicaciones-service/pom.xml test
./consultas-service/mvnw.cmd -f consultas-service/pom.xml test
```

Usa las peticiones existentes de Bruno para crear un investigador con un correo nuevo y dos publicaciones. Cambia el id en la URL de “Eliminar investigador”, usa el ambiente `gateway` y verifica la cabecera `X-Correlation-Id: demo-001`; busca ese valor en los tres logs.

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8081/actuator/health
curl.exe http://localhost:8083/actuator/health
curl.exe -i -H "X-Correlation-Id: demo-001" http://localhost:8000/api/investigadores
curl.exe http://localhost:8080/actuator/metrics/http.server.requests
curl.exe "http://localhost:8080/actuator/metrics/http.server.requests?tag=status:404"
docker exec rica-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group consultas-service
docker exec rica-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group publicaciones-service
```

Para el lag del paso 7, detén consultas, crea tres investigadores, espera a que el Outbox publique y consulta el grupo `consultas-service`. Reinicia consultas y espera el rebalanceo de Kafka antes de comprobar que el lag vuelva a cero.

Para el paso 8, crea primero el investigador y su publicación, y espera a que la ficha aparezca en consultas. Después detén publicaciones, elimina al investigador con `demo-002`, consulta las fichas pendientes y mide el lag; reinicia publicaciones y verifica la eliminación final.

```powershell
curl.exe "http://localhost:8083/api/consultas/investigadores?estado=EN_ELIMINACION"
```

Hay dos ajustes respecto al enunciado: el grupo activo en este repositorio es `consultas-service`, pues `consultas-service-v2` está comentado; además, la publicación del paso 8 debe registrarse antes de detener el servicio que la crea.

## Entrega

La rama ya se llamaba `taller-observabilidad` al comenzar. Los cambios previos de Bruno se conservaron y no se ejecutó `git add`, `git commit` ni `git push`; la publicación de la rama, el PR hacia `main` y el enlace en Moodle quedan a cargo del estudiante.

Para Moodle, adjunta las evidencias de los grupos 1 a 4, la nota de brecha y `5-discusion.md`. Los archivos de preparación muestran qué registros se usaron, y `6-pruebas-maven.txt` sirve como comprobación adicional.

La compensación y la alerta por antigüedad se diseñaron por escrito, tal como solicita el paso 9; no son funciones implementadas.

Al terminar se dejaron activos Eureka, el gateway y los tres servicios con observabilidad, junto con PostgreSQL, Kafka y MongoDB. Los logs completos y los PID de esta sesión están en `target/observabilidad/`, una carpeta temporal ignorada por Git; las evidencias de entrega están copiadas en esta carpeta. Se conservaron los tres investigadores de la prueba de lag y las publicaciones huérfanas de los escenarios, sin borrar datos anteriores.

# Taller lección 11 · Contenedores y CI

## Paso 2 · El Dockerfile multi-etapa

**(a) ¿Por qué se copia primero `pom.xml` y luego `src`?**
Docker guarda cada instrucción como una capa en caché y solo la vuelve a ejecutar si cambia lo que copió.
El `pom.xml` cambia poco y el código cambia mucho. Si el pom va primero, la capa de
`mvn dependency:go-offline` (la descarga de dependencias, que es lo lento) se reutiliza mientras el pom
no cambie, y al tocar el código solo se repiten `COPY src` y `mvn package`.

**(b) ¿Qué contiene la imagen final y qué no?**
Contiene el JRE 25 de Temurin sobre Ubuntu, el usuario `rica` y el `app.jar` ejecutable.
No contiene Maven, el JDK (compilador), el código fuente, el repositorio `~/.m2` ni la carpeta `target/`:
todo eso se quedó en la etapa `build`, que se descarta. Por eso es más liviana y expone menos superficie de ataque.

**(c) ¿Por qué `USER rica`?**
Para que el proceso Java no corra como root dentro del contenedor. Si alguien explota una vulnerabilidad
de la aplicación, obtiene los permisos de un usuario de sistema sin privilegios, sin shell y sin home, y le
cuesta más modificar el sistema de archivos o escapar al host.

## Paso 3 · Capas reutilizadas

<!-- Pega aquí la salida de `docker images rica/eureka-server` y de la segunda/tercera construcción. -->

Tras cambiar solo `application.yml`:
- `CACHED`: `FROM maven…`, `WORKDIR`, `COPY pom.xml`, `RUN mvn dependency:go-offline`, y en la etapa final `FROM eclipse-temurin:25-jre`, `WORKDIR`, `RUN useradd`.
- Se ejecutan de nuevo: `COPY src ./src`, `RUN mvn package` y `COPY --from=build … app.jar`.

## Paso 5 · Una imagen, varias configuraciones

Con una sola imagen, lo que se probó es exactamente lo que llega a producción: el mismo binario, con las
mismas dependencias y el mismo JRE. Solo cambian las variables de entorno (puertos, URLs, credenciales).
Con una imagen por entorno habría que reconstruir para cada uno y nada garantizaría que las tres sean
iguales; además, los secretos quedarían horneados dentro de la imagen. Es el principio de *config* de
twelve-factor: el código va en el artefacto y la configuración en el entorno.

## Paso 6 · Tabla de decisión de almacenes

<!-- TODO: ajusta la tabla al módulo de TU proyecto de grupo. Abajo un ejemplo con RICA. -->

| Dato | Almacén | Por qué | Consulta principal que lo justifica | Costo de añadirlo |
|---|---|---|---|---|
| Investigadores | PostgreSQL | Datos estructurados, correo único, transacciones con el outbox | `SELECT * FROM investigador WHERE correo = ?` | Ninguno: ya existe |
| Publicaciones | PostgreSQL | Relación con el investigador, reglas como el límite anual por investigador | `SELECT COUNT(*) … WHERE investigador = ? AND anio = ?` | Ninguno: ya existe |
| Ficha de investigador (lectura CQRS) | MongoDB | Documento desnormalizado que se lee entero, sin joins | `findById(correo)` | Ya existe; otro motor que operar y respaldar |
| Ficha cacheada | Redis (cache-aside, TTL 30 s) | Lecturas muy repetidas de la misma ficha, en memoria | `GET ficha:{correo}` | Un contenedor más, invalidar con `DEL` en cada evento; riesgo de datos viejos dentro del TTL |
| Eventos de dominio | Kafka | Log durable y ordenado que varios consumidores leen a su ritmo | consumir `investigador-eliminado` desde un offset | Ya existe; operar brokers y monitorear el lag |
| Búsqueda por texto en títulos | PostgreSQL (`tsvector` + índice GIN) | El volumen no justifica Elasticsearch | `WHERE to_tsvector(titulo) @@ plainto_tsquery(?)` | Solo una columna e índice; Elasticsearch costaría otro clúster y sincronización |

**¿Hace falta más de una tecnología?**
Para el volumen de RICA, una base relacional bien usada cubre casi todo: PostgreSQL resuelve las entidades,
la búsqueda de texto y hasta los documentos JSON con `jsonb`. MongoDB y Kafka se justifican por la
arquitectura (CQRS y eventos entre servicios), no por el volumen. Redis no se justifica todavía: la ficha ya
se lee de una proyección precalculada en MongoDB por clave primaria, y una caché solo añadiría invalidación y
datos potencialmente viejos. Lo añadiría solo si una medición mostrara que esa lectura es un cuello de botella.

## Lectura opcional · ¿Qué haría falta para ejecutar consultas-service como contenedor?

1. Un Dockerfile igual al de eureka-server con `EXPOSE` del puerto de consultas-service.
2. Una red de Docker compartida con Kafka, MongoDB y Eureka (un solo `docker-compose.yml` con todos).
3. Cambiar `localhost` por el nombre del servicio por variables de entorno: `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`, `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/rica_consultas`, `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/`.
4. Configurar Kafka con dos listeners (uno interno `kafka:29092` para la red de Docker y otro externo `localhost:9092` para el host), porque el broker anuncia una dirección que el cliente debe poder alcanzar.
5. `depends_on` con `healthcheck` para que el servicio no arranque antes que Kafka y MongoDB, y `eureka.instance.prefer-ip-address: true` para que el gateway lo encuentre por IP del contenedor.

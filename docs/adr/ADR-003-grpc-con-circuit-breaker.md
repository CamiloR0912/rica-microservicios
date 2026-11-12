# ADR-003: Verificar al investigador con gRPC síncrono protegido por un Circuit Breaker

- Estado: Aceptada
- Fecha: 2026-10-11

## Contexto
Antes de guardar una publicación, `publicaciones-service` debe confirmar que el correo del investigador
existe en `investigadores-service`. Es una **consulta**: sin la respuesta no se puede decidir, así que tiene
que ser síncrona. Las fuerzas en juego: si investigadores está lento o caído, publicaciones no debe agotar
sus hilos esperando, y el contrato entre ambos debe ser explícito y tipado.

## Decisión
- Contrato gRPC `InvestigadorGrpc.ExisteInvestigador(correoInstitucional) → existe`. Investigadores lo
  sirve en el puerto 9090 y publicaciones tiene su propia copia del `.proto`.
- Del lado cliente, `VerificadorInvestigadorGrpc` usa un deadline de 5 s y un **Circuit Breaker**
  (Resilience4j): ventana de 10 llamadas, mínimo 5, umbral de fallo del 50 % y 10 s abierto.
- Si el circuito está abierto o gRPC falla, se lanza `ServicioNoDisponibleException`, que se responde
  como **HTTP 503**. Publicaciones solo depende del puerto `VerificadorInvestigador` (capa anticorrupción).

## Alternativas descartadas
- **REST/JSON entre servicios.** Más fácil de depurar con curl. Se descartó porque gRPC da un contrato
  tipado generado desde el `.proto` y menor costo por llamada.
- **Réplica local de investigadores en publicaciones, alimentada por `investigadores.registrados`.**
  Eliminaría la dependencia síncrona. Se descartó por ahora porque introduce una ventana de inconsistencia:
  un investigador recién creado no podría publicar hasta que llegue el evento. Es la evolución natural si
  la disponibilidad pasa a ser el primer atributo.
- **Llamar sin Circuit Breaker, solo con timeout.** Se descartó porque con investigadores caído cada
  petición esperaría los 5 s completos.

## Consecuencias
+ Fallo rápido: con el circuito abierto, publicaciones responde 503 sin tocar la red.
+ El contrato está versionado en el `.proto`.
- Es una dependencia temporal: si investigadores está caído, no se pueden registrar publicaciones.
- El `.proto` está duplicado en los dos servicios y puede divergir: no hay un repositorio de contratos.
- El canal usa `static://localhost:9090`, no Eureka: no balancea entre varias instancias (riesgo R5).
- gRPC es más difícil de probar a mano que REST (hace falta grpcurl o Bruno con soporte gRPC).

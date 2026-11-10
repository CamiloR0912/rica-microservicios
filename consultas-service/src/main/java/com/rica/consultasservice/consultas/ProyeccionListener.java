package com.rica.consultasservice.consultas;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

/** Adaptador de entrada del lado de lectura: escucha los eventos y delega en el proyector. */
@Component
public class ProyeccionListener {

    private final ProyectorFichas proyector;
    private final ObjectMapper objectMapper;

    public ProyeccionListener(ProyectorFichas proyector, ObjectMapper objectMapper) {
        this.proyector = proyector;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "investigadores.registrados")
    public void investigadorRegistrado(String mensaje) {
        conCorrelacion(mensaje, proyector::alRegistrarseInvestigador);
    }

    @KafkaListener(topics = "publicaciones.registradas")
    public void publicacionRegistrada(String mensaje) {
        conCorrelacion(mensaje, proyector::alRegistrarsePublicacion);
    }

    @KafkaListener(topics = "investigadores.en-eliminacion")
    public void investigadorEnEliminacion(String mensaje) {
        conCorrelacion(mensaje, proyector::alIniciarseEliminacion);
    }

    @KafkaListener(topics = "investigadores.eliminados")
    public void investigadorEliminado(String mensaje) {
        conCorrelacion(mensaje, proyector::alEliminarseInvestigador);
    }

    private void conCorrelacion(String mensaje, Consumer<String> proyectar) {
        String correlationId = objectMapper.readTree(mensaje)
                .path("correlationId").asString("sin-correlacion");
        try (MDC.MDCCloseable ignorado = MDC.putCloseable("correlationId", correlationId)) {
            proyectar.accept(mensaje);
        }
    }

}

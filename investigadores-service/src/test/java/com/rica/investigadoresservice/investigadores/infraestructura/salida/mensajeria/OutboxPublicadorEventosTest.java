package com.rica.investigadoresservice.investigadores.infraestructura.salida.mensajeria;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPublicadorEventosTest {

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "demo-001")
    void guardaLaTrazaSinModificarElContenidoOriginal(String id) {
        OutboxEventRepository repositorio = mock(OutboxEventRepository.class);
        JsonMapper mapper = JsonMapper.builder().build();
        OutboxPublicadorEventos publicador = new OutboxPublicadorEventos(repositorio, mapper);
        Map<String, Object> contenido = Map.of("correoInstitucional", "ana@uptc.edu.co");
        if (id != null) {
            MDC.put("correlationId", id);
        }

        publicador.publicar("investigadores.en-eliminacion", "ana@uptc.edu.co", contenido);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repositorio).save(captor.capture());
        OutboxEvent evento = captor.getValue();
        JsonNode payload = mapper.readTree(evento.getPayload());
        assertThat(evento.getTopico()).isEqualTo("investigadores.en-eliminacion");
        assertThat(evento.getClave()).isEqualTo("ana@uptc.edu.co");
        assertThat(payload.get("correoInstitucional").asString()).isEqualTo("ana@uptc.edu.co");
        assertThat(payload.path("correlationId").asString(null)).isEqualTo(id);
        assertThat(contenido).doesNotContainKey("correlationId");
    }
}

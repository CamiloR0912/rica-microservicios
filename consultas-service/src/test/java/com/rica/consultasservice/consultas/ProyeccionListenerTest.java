package com.rica.consultasservice.consultas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProyeccionListenerTest {

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void todosLosEventosProyectanConSuTrazaYLimpianAlTerminar(boolean conTraza) {
        ProyectorFichas proyector = mock(ProyectorFichas.class, invocacion -> {
            assertThat(MDC.get("correlationId")).isEqualTo(conTraza ? "demo-001" : "sin-correlacion");
            return null;
        });
        ProyeccionListener listener = new ProyeccionListener(proyector, JsonMapper.builder().build());
        String mensaje = conTraza ? "{\"correlationId\":\"demo-001\"}" : "{}";

        listener.investigadorRegistrado(mensaje);
        assertThat(MDC.get("correlationId")).isNull();
        listener.publicacionRegistrada(mensaje);
        assertThat(MDC.get("correlationId")).isNull();
        listener.investigadorEnEliminacion(mensaje);
        assertThat(MDC.get("correlationId")).isNull();
        listener.investigadorEliminado(mensaje);
        assertThat(MDC.get("correlationId")).isNull();
        verify(proyector).alRegistrarseInvestigador(mensaje);
        verify(proyector).alRegistrarsePublicacion(mensaje);
        verify(proyector).alIniciarseEliminacion(mensaje);
        verify(proyector).alEliminarseInvestigador(mensaje);
    }

    @Test
    void propagaElErrorAlManejadorKafkaYLimpiaElId() {
        ProyectorFichas proyector = mock(ProyectorFichas.class);
        ProyeccionListener listener = new ProyeccionListener(proyector, JsonMapper.builder().build());
        String mensaje = "{\"correlationId\":\"demo-error\"}";
        doThrow(new IllegalStateException("fallo")).when(proyector).alIniciarseEliminacion(mensaje);

        assertThatThrownBy(() -> listener.investigadorEnEliminacion(mensaje))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }
}

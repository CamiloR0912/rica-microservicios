package com.rica.investigadoresservice.investigadores.infraestructura.entrada.mensajeria;

import com.rica.investigadoresservice.investigadores.aplicacion.InvestigadorUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PublicacionesMarcadasListenerTest {

    private final InvestigadorUseCase servicio = mock(InvestigadorUseCase.class);
    private final PublicacionesMarcadasListener listener =
            new PublicacionesMarcadasListener(servicio, JsonMapper.builder().build());

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void retomaLaTrazaDuranteLaConfirmacionYLimpiaDespues(boolean conTraza) {
        doAnswer(invocacion -> {
            assertThat(MDC.get("correlationId")).isEqualTo(conTraza ? "demo-001" : "sin-correlacion");
            return null;
        }).when(servicio).confirmarEliminacion("ana@uptc.edu.co");

        listener.alMarcarsePublicaciones("{\"correoInstitucional\":\"ana@uptc.edu.co\""
                + (conTraza ? ",\"correlationId\":\"demo-001\"}" : "}"));

        verify(servicio).confirmarEliminacion("ana@uptc.edu.co");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void propagaElFalloYLimpiaLaTraza() {
        doThrow(new IllegalStateException("fallo")).when(servicio).confirmarEliminacion("ana@uptc.edu.co");

        assertThatThrownBy(() -> listener.alMarcarsePublicaciones(
                "{\"correoInstitucional\":\"ana@uptc.edu.co\",\"correlationId\":\"demo-error\"}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }
}

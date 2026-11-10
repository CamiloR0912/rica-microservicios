package com.rica.publicacionesservice.publicaciones;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvestigadorEnEliminacionListenerTest {

    private final PublicacionRepository repositorio = mock(PublicacionRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final InvestigadorEnEliminacionListener listener =
            new InvestigadorEnEliminacionListener(repositorio, kafka, mapper);

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void marcaHuerfanasYReenviaLaCorrelacion(boolean conTraza) {
        String id = conTraza ? "demo-001" : "sin-correlacion";
        Publicacion publicacion = new Publicacion("1", "ana@uptc.edu.co", "Articulo", "articulo", 2026, Map.of());
        when(repositorio.findByInvestigadorCorreo("ana@uptc.edu.co")).thenAnswer(invocacion -> {
            assertThat(MDC.get("correlationId")).isEqualTo(id);
            return List.of(publicacion);
        });

        listener.alIniciarseEliminacion("{\"correoInstitucional\":\"ana@uptc.edu.co\""
                + (conTraza ? ",\"correlationId\":\"demo-001\"}" : "}"));

        assertThat(publicacion.isHuerfana()).isTrue();
        verify(repositorio).saveAll(List.of(publicacion));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("publicaciones.marcadas-huerfanas"), eq("ana@uptc.edu.co"), captor.capture());
        JsonNode confirmacion = mapper.readTree(captor.getValue());
        assertThat(confirmacion.get("correlationId").asString()).isEqualTo(id);
        assertThat(confirmacion.get("cantidad").asInt()).isEqualTo(1);
        assertThat(confirmacion.get("correoInstitucional").asString()).isEqualTo("ana@uptc.edu.co");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void noConfirmaSiFallaElGuardadoYLimpiaLaTraza() {
        when(repositorio.findByInvestigadorCorreo("ana@uptc.edu.co")).thenReturn(List.of());
        when(repositorio.saveAll(anyList())).thenThrow(new IllegalStateException("fallo"));

        assertThatThrownBy(() -> listener.alIniciarseEliminacion(
                "{\"correoInstitucional\":\"ana@uptc.edu.co\",\"correlationId\":\"demo-error\"}"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(kafka);
        assertThat(MDC.get("correlationId")).isNull();
    }
}

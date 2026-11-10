package com.rica.investigadoresservice.investigadores.infraestructura.entrada.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void conservaLaCabeceraDuranteLaPeticionYLimpiaAlTerminar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CABECERA, "demo-001");

        filtro.doFilter(request, response, (req, res) ->
                assertThat(MDC.get("correlationId")).isEqualTo("demo-001"));

        assertThat(response.getHeader(CorrelationIdFilter.CABECERA)).isEqualTo("demo-001");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void generaUuidSiNoRecibeUnId(String cabecera) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        if (cabecera != null) {
            request.addHeader(CorrelationIdFilter.CABECERA, cabecera);
        }

        filtro.doFilter(request, response, (req, res) -> {
            String id = response.getHeader(CorrelationIdFilter.CABECERA);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(MDC.get("correlationId")).isEqualTo(id);
        });

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void limpiaElIdInclusoSiFallaLaPeticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CABECERA, "demo-error");

        assertThatThrownBy(() -> filtro.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("fallo"); }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}

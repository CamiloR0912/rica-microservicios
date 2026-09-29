package com.rica.publicacionesservice.publicaciones;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitePublicacionesAnualesServiceTest {

    @Mock
    private PublicacionRepository publicacionRepository;

    @InjectMocks
    private LimitePublicacionesAnualesService limitePublicacionesAnualesService;

    @Test
    void puedeRegistrarCuandoAunNoLlegaAlMaximo() {
        when(publicacionRepository.countByInvestigadorCorreoAndAnio("ana.torres@uptc.edu.co", 2026)).thenReturn(4L);

        boolean puede = limitePublicacionesAnualesService.puedeRegistrar("ana.torres@uptc.edu.co", 2026);

        assertThat(puede).isTrue();
    }

    @Test
    void noPuedeRegistrarCuandoYaAlcanzoElMaximo() {
        when(publicacionRepository.countByInvestigadorCorreoAndAnio("ana.torres@uptc.edu.co", 2026)).thenReturn(5L);

        boolean puede = limitePublicacionesAnualesService.puedeRegistrar("ana.torres@uptc.edu.co", 2026);

        assertThat(puede).isFalse();
    }

}

package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.investigadores.dominio.CorreoDuplicadoException;
import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigadorFactoryTest {

    @Mock
    private RepositorioInvestigadores repositorioInvestigadores;

    @InjectMocks
    private InvestigadorFactory investigadorFactory;

    @Test
    void crearConstruyeUnInvestigadorValidoCuandoElCorreoNoExiste() {
        when(repositorioInvestigadores.existeCorreo("ana.torres@uptc.edu.co")).thenReturn(false);

        Investigador investigador = investigadorFactory.crear("Ana Torres", "ana.torres@uptc.edu.co", "GIT-UPTC");

        assertThat(investigador.getId()).isNull();
        assertThat(investigador.getNombreCompleto()).isEqualTo("Ana Torres");
        assertThat(investigador.getCorreoInstitucional().valor()).isEqualTo("ana.torres@uptc.edu.co");
        assertThat(investigador.getGrupoInvestigacion()).isEqualTo("GIT-UPTC");
    }

    @Test
    void crearRechazaUnCorreoInstitucionalYaRegistrado() {
        when(repositorioInvestigadores.existeCorreo("carlos.ruiz@uptc.edu.co")).thenReturn(true);

        assertThatThrownBy(() -> investigadorFactory.crear("Carlos Ruiz", "carlos.ruiz@uptc.edu.co", "GIT-UPTC"))
                .isInstanceOf(CorreoDuplicadoException.class);

        verify(repositorioInvestigadores).existeCorreo("carlos.ruiz@uptc.edu.co");
    }

    @Test
    void crearRechazaUnCorreoFueraDelDominioInstitucionalAntesDeConsultarElRepositorio() {
        assertThatThrownBy(() -> investigadorFactory.crear("Carlos Ruiz", "carlos.ruiz@gmail.com", "GIT-UPTC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EquipoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** EST-02 (parte de equipos): un estadio con equipos no se elimina. Sin Spring ni base de datos. */
@ExtendWith(MockitoExtension.class)
class EstadioConEquiposVerificadorTest {

    @Mock
    private EquipoRepository equipoRepository;

    @InjectMocks
    private EstadioConEquiposVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Estadio")
    void entidad_esEstadio() {
        assertThat(verificador.entidad()).isEqualTo(Estadio.class);
    }

    @Test
    @DisplayName("EST-02 positivo: un estadio que no es local de ningún equipo se puede eliminar")
    void est02_estadioSinEquipos_noLanzaNada() {
        when(equipoRepository.existsByEstadioLocalId(5L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EST-02 negativo: un estadio que es local de algún equipo lanza 409 con código EST-02")
    void est02_estadioConEquipos_lanzaConflicto() {
        when(equipoRepository.existsByEstadioLocalId(5L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(5L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));
    }
}

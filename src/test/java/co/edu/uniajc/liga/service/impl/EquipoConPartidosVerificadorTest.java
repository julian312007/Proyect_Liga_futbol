package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.PartidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Un equipo que juega o jugó partidos, como local o como visitante, no se elimina (409 CONFLICTO_DEPENDENCIAS). */
@ExtendWith(MockitoExtension.class)
class EquipoConPartidosVerificadorTest {

    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private EquipoConPartidosVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Equipo")
    void vigilaEquipo() {
        assertThat(verificador.entidad()).isEqualTo(Equipo.class);
    }

    @Test
    @DisplayName("Un equipo con partidos (local o visitante) no se puede eliminar: 409 CONFLICTO_DEPENDENCIAS")
    void equipoConPartidos_lanza409() {
        when(partidoRepository.existsByEquipoLocalIdOrEquipoVisitanteId(10L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(10L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("Un equipo sin partidos puede eliminarse; se consultó como local y como visitante")
    void equipoSinPartidos_noLanza() {
        when(partidoRepository.existsByEquipoLocalIdOrEquipoVisitanteId(10L, 10L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(10L)).doesNotThrowAnyException();

        verify(partidoRepository).existsByEquipoLocalIdOrEquipoVisitanteId(10L, 10L);
    }
}

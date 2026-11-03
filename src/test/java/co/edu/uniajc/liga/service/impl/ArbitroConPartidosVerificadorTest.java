package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Arbitro;
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
import static org.mockito.Mockito.when;

/** Un árbitro con partidos no se elimina (409 CONFLICTO_DEPENDENCIAS). */
@ExtendWith(MockitoExtension.class)
class ArbitroConPartidosVerificadorTest {

    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private ArbitroConPartidosVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Arbitro")
    void vigilaArbitro() {
        assertThat(verificador.entidad()).isEqualTo(Arbitro.class);
    }

    @Test
    @DisplayName("Un árbitro con partidos no se puede eliminar: 409 CONFLICTO_DEPENDENCIAS")
    void arbitroConPartidos_lanza409() {
        when(partidoRepository.existsByArbitroId(30L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(30L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("Un árbitro sin partidos puede eliminarse")
    void arbitroSinPartidos_noLanza() {
        when(partidoRepository.existsByArbitroId(30L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(30L)).doesNotThrowAnyException();
    }
}

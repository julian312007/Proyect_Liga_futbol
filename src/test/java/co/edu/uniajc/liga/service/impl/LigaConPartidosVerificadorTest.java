package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Liga;
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

/** Una liga con partidos no se elimina (409 CONFLICTO_DEPENDENCIAS). */
@ExtendWith(MockitoExtension.class)
class LigaConPartidosVerificadorTest {

    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private LigaConPartidosVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Liga")
    void vigilaLiga() {
        assertThat(verificador.entidad()).isEqualTo(Liga.class);
    }

    @Test
    @DisplayName("Una liga con partidos no se puede eliminar: 409 CONFLICTO_DEPENDENCIAS")
    void ligaConPartidos_lanza409() {
        when(partidoRepository.existsByLigaId(1L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(1L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("Una liga sin partidos puede eliminarse")
    void ligaSinPartidos_noLanza() {
        when(partidoRepository.existsByLigaId(1L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(1L)).doesNotThrowAnyException();
    }
}

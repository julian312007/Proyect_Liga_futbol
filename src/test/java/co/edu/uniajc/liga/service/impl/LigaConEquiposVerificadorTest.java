package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Liga;
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

/** Una liga con equipos no se elimina. Sin Spring ni base de datos. */
@ExtendWith(MockitoExtension.class)
class LigaConEquiposVerificadorTest {

    @Mock
    private EquipoRepository equipoRepository;

    @InjectMocks
    private LigaConEquiposVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Liga")
    void entidad_esLiga() {
        assertThat(verificador.entidad()).isEqualTo(Liga.class);
    }

    @Test
    @DisplayName("Liga positivo: una liga sin equipos se puede eliminar")
    void ligaSinEquipos_noLanzaNada() {
        when(equipoRepository.existsByLigaId(3L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Liga negativo: una liga con equipos lanza 409 CONFLICTO_DEPENDENCIAS")
    void ligaConEquipos_lanzaConflicto() {
        when(equipoRepository.existsByLigaId(3L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(3L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Estadio;
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

/** EST-02 (parte de los partidos): un estadio con partidos no se elimina. */
@ExtendWith(MockitoExtension.class)
class EstadioConPartidosVerificadorTest {

    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private EstadioConPartidosVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Estadio")
    void vigilaEstadio() {
        assertThat(verificador.entidad()).isEqualTo(Estadio.class);
    }

    @Test
    @DisplayName("EST-02 (negativo): un estadio con partidos no se puede eliminar, 409 EST-02")
    void est02_estadioConPartidos_lanza409() {
        when(partidoRepository.existsByEstadioId(20L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(20L))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));
    }

    @Test
    @DisplayName("EST-02 (positivo): un estadio sin partidos puede eliminarse")
    void est02_estadioSinPartidos_noLanza() {
        when(partidoRepository.existsByEstadioId(20L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(20L)).doesNotThrowAnyException();
    }
}

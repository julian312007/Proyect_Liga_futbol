package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link JugadorConEventosVerificador} (punto de extensión de borrado, contrato §6): un
 * jugador con goles o tarjetas no se elimina (409 {@code CONFLICTO_DEPENDENCIAS}).
 */
@ExtendWith(MockitoExtension.class)
class JugadorConEventosVerificadorTest {

    private static final Long JUGADOR_ID = 101L;

    @Mock
    private GolRepository golRepository;
    @Mock
    private TarjetaRepository tarjetaRepository;

    private JugadorConEventosVerificador verificador;

    @BeforeEach
    void preparar() {
        verificador = new JugadorConEventosVerificador(golRepository, tarjetaRepository);
    }

    @Test
    @DisplayName("Vigila la entidad Jugador")
    void vigilaJugador() {
        assertThat(verificador.entidad()).isEqualTo(Jugador.class);
    }

    @Test
    @DisplayName("Sin goles ni tarjetas (positivo): el jugador se puede eliminar, no lanza nada")
    void sinEventos_noLanzaNada() {
        when(golRepository.existsByJugadorId(JUGADOR_ID)).thenReturn(false);
        when(tarjetaRepository.existsByJugadorId(JUGADOR_ID)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(JUGADOR_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con goles (negativo): 409 CONFLICTO_DEPENDENCIAS")
    void conGoles_lanza409() {
        when(golRepository.existsByJugadorId(JUGADOR_ID)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(JUGADOR_ID))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));

        verifyNoInteractions(tarjetaRepository);
    }

    @Test
    @DisplayName("Con tarjetas pero sin goles (negativo): 409 CONFLICTO_DEPENDENCIAS")
    void conTarjetas_lanza409() {
        when(golRepository.existsByJugadorId(JUGADOR_ID)).thenReturn(false);
        when(tarjetaRepository.existsByJugadorId(JUGADOR_ID)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(JUGADOR_ID))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasGolRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.gol;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.jugador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tabla de goleadores (F5): NORMAL y PENAL suman, AUTOGOL no. Repositorios simulados, sin Spring. */
@ExtendWith(MockitoExtension.class)
class GoleadoresServiceImplTest {

    private static final long LIGA = 7L;

    @Mock
    private LigaRepository ligaRepository;
    @Mock
    private EstadisticasGolRepository golRepository;

    @InjectMocks
    private GoleadoresServiceImpl servicio;

    private final Equipo aguilas = equipo(1, "Águilas");
    private final Equipo buhos = equipo(2, "Búhos");

    @Test
    @DisplayName("Los goles NORMAL y PENAL suman al goleador")
    void golesNormalYPenal_suman() {
        Jugador ana = jugador(10, "Ana Ruiz", aguilas);

        List<GoleadorResponse> tabla = goleadores(List.of(
                gol(1, ana, TipoGol.NORMAL), gol(2, ana, TipoGol.PENAL), gol(3, ana, TipoGol.NORMAL)));

        assertThat(tabla).containsExactly(new GoleadorResponse(1, 10L, "Ana Ruiz", 1L, "Águilas", 3));
    }

    @Test
    @DisplayName("El AUTOGOL no suma: un jugador solo con autogoles no aparece y los de otro tipo se cuentan aparte")
    void autogol_noSuma() {
        Jugador ana = jugador(10, "Ana Ruiz", aguilas);
        Jugador luis = jugador(11, "Luis Mora", buhos);

        List<GoleadorResponse> tabla = goleadores(List.of(
                gol(1, ana, TipoGol.NORMAL),
                gol(2, ana, TipoGol.AUTOGOL),
                gol(3, luis, TipoGol.AUTOGOL)));

        // Ana: 1 NORMAL cuenta, 1 AUTOGOL no. Luis: solo un AUTOGOL, no aparece.
        assertThat(tabla).containsExactly(new GoleadorResponse(1, 10L, "Ana Ruiz", 1L, "Águilas", 1));
    }

    @Test
    @DisplayName("Orden: más goles primero; con los mismos goles, por nombre; las posiciones van de 1 en 1")
    void orden_porGolesYLuegoPorNombre() {
        Jugador zoe = jugador(12, "Zoe Gil", buhos);
        Jugador ana = jugador(10, "Ana Ruiz", aguilas);
        Jugador beto = jugador(11, "Beto Paz", aguilas);

        // Zoe 3 goles; Ana 2; Beto 2 -> Zoe, Ana, Beto (Ana antes que Beto por nombre).
        List<GoleadorResponse> tabla = goleadores(List.of(
                gol(1, beto, TipoGol.NORMAL), gol(2, zoe, TipoGol.NORMAL), gol(3, ana, TipoGol.PENAL),
                gol(4, zoe, TipoGol.PENAL), gol(5, beto, TipoGol.NORMAL), gol(6, ana, TipoGol.NORMAL),
                gol(7, zoe, TipoGol.NORMAL)));

        assertThat(tabla).extracting(GoleadorResponse::jugador).containsExactly("Zoe Gil", "Ana Ruiz", "Beto Paz");
        assertThat(tabla).extracting(GoleadorResponse::goles).containsExactly(3, 2, 2);
        assertThat(tabla).extracting(GoleadorResponse::posicion).containsExactly(1, 2, 3);
        assertThat(tabla.get(0).equipo()).isEqualTo("Búhos");
    }

    @Test
    @DisplayName("Una liga sin goles devuelve una tabla vacía")
    void sinGoles_devuelveTablaVacia() {
        assertThat(goleadores(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Solo se piden los goles de partidos FINALIZADO")
    void consultaSoloGolesDePartidosFinalizados() {
        goleadores(List.of());

        verify(golRepository).buscarDeLigaPorEstadoDelPartido(LIGA, EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Liga inexistente: RecursoNoEncontradoException (404) y no se consultan goles")
    void ligaInexistente_lanza404() {
        when(ligaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.obtenerGoleadores(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Liga")
                .hasMessageContaining("99");

        verifyNoInteractions(golRepository);
    }

    private List<GoleadorResponse> goleadores(List<Gol> goles) {
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(golRepository.buscarDeLigaPorEstadoDelPartido(LIGA, EstadoPartido.FINALIZADO)).thenReturn(goles);
        return servicio.obtenerGoleadores(LIGA);
    }
}

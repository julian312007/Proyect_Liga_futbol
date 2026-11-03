package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasEquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.finalizado;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.partido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tabla de posiciones (F4, RN-01). Todos los resultados esperados están calculados a mano en cada prueba; el
 * servicio se prueba con repositorios simulados, sin Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class TablaPosicionesServiceImplTest {

    private static final long LIGA = 7L;

    @Mock
    private LigaRepository ligaRepository;
    @Mock
    private EstadisticasEquipoRepository equipoRepository;
    @Mock
    private EstadisticasPartidoRepository partidoRepository;

    @InjectMocks
    private TablaPosicionesServiceImpl servicio;

    // ------------------------------------------------------------------ RN-01

    @Test
    @DisplayName("RN-01: victoria suma 3 puntos, empate 1 y derrota 0, con PJ, PG, PE, PP, GF, GC y DG calculados a mano")
    void rn01_victoriaEmpateYDerrota_sumanTresUnoYCero() {
        Equipo aguilas = equipo(1, "Águilas");
        Equipo buhos = equipo(2, "Búhos");
        Equipo condores = equipo(3, "Cóndores");
        Equipo delfines = equipo(4, "Delfines");
        // Águilas 2-0 Búhos | Cóndores 1-1 Delfines | Águilas 1-1 Cóndores | Búhos 3-1 Delfines
        List<Partido> partidos = List.of(
                finalizado(1, aguilas, 2, buhos, 0),
                finalizado(2, condores, 1, delfines, 1),
                finalizado(3, aguilas, 1, condores, 1),
                finalizado(4, buhos, 3, delfines, 1));

        List<PosicionEquipoResponse> tabla = calcular(List.of(aguilas, buhos, condores, delfines), partidos);

        // Águilas: G(2-0) + E(1-1)   -> PJ2 PG1 PE1 PP0 GF3 GC1 DG+2 Pts 3+1 = 4
        // Búhos:   P(0-2) + G(3-1)   -> PJ2 PG1 PE0 PP1 GF3 GC3 DG 0 Pts 3+0 = 3
        // Cóndores: E(1-1) + E(1-1)  -> PJ2 PG0 PE2 PP0 GF2 GC2 DG 0 Pts 1+1 = 2
        // Delfines: E(1-1) + P(1-3)  -> PJ2 PG0 PE1 PP1 GF2 GC4 DG-2 Pts 1+0 = 1
        assertThat(tabla).containsExactly(
                new PosicionEquipoResponse(1, 1L, "Águilas", 2, 1, 1, 0, 3, 1, 2, 4),
                new PosicionEquipoResponse(2, 2L, "Búhos", 2, 1, 0, 1, 3, 3, 0, 3),
                new PosicionEquipoResponse(3, 3L, "Cóndores", 2, 0, 2, 0, 2, 2, 0, 2),
                new PosicionEquipoResponse(4, 4L, "Delfines", 2, 0, 1, 1, 2, 4, -2, 1));
    }

    @Test
    @DisplayName("RN-01: un empate da 1 punto a cada equipo, no los 3 de una victoria")
    void rn01_empate_daUnPuntoACadaEquipo() {
        Equipo rojos = equipo(1, "Rojos");
        Equipo azules = equipo(2, "Azules");

        List<PosicionEquipoResponse> tabla = calcular(List.of(rojos, azules), List.of(finalizado(1, rojos, 2, azules, 2)));

        assertThat(tabla).extracting(PosicionEquipoResponse::pts).containsExactly(1, 1);
        assertThat(tabla).extracting(PosicionEquipoResponse::pe).containsExactly(1, 1);
        assertThat(tabla).extracting(PosicionEquipoResponse::pg).containsExactly(0, 0);
    }

    @Test
    @DisplayName("RN-01: los partidos EN_JUEGO y PROGRAMADO no suman puntos, goles ni partidos jugados")
    void rn01_partidosSinFinalizar_noCuentan() {
        Equipo a = equipo(1, "Equipo A");
        Equipo b = equipo(2, "Equipo B");
        List<Partido> partidos = List.of(
                finalizado(1, a, 1, b, 0),
                partido(2, a, b, 5, 0, EstadoPartido.EN_JUEGO),
                partido(3, b, a, 9, 0, EstadoPartido.PROGRAMADO));

        List<PosicionEquipoResponse> tabla = calcular(List.of(a, b), partidos);

        // Solo cuenta el 1-0: A tiene 3 puntos y 1 gol; B, 0 puntos y 1 gol en contra.
        assertThat(tabla).containsExactly(
                new PosicionEquipoResponse(1, 1L, "Equipo A", 1, 1, 0, 0, 1, 0, 1, 3),
                new PosicionEquipoResponse(2, 2L, "Equipo B", 1, 0, 0, 1, 0, 1, -1, 0));
    }

    @Test
    @DisplayName("RN-01: solo se piden a la base de datos los partidos FINALIZADO de la liga")
    void rn01_consultaSoloLosPartidosFinalizados() {
        calcular(List.of(), List.of());

        verify(partidoRepository).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
    }

    // ----------------------------------------------------------------- Orden

    @Test
    @DisplayName("Orden: empate en puntos se desempata por diferencia de gol y luego por goles a favor")
    void orden_empateEnPuntos_desempataPorDiferenciaYGolesAFavor() {
        Equipo pumas = equipo(11, "Pumas");
        Equipo quetzales = equipo(12, "Quetzales");
        Equipo ranas = equipo(13, "Ranas");
        Equipo sapos = equipo(14, "Sapos");
        // Pumas 2-0 Sapos | Quetzales 3-1 Sapos | Sapos 0-1 Ranas
        // Pumas 3 pts DG+2 GF2 | Quetzales 3 pts DG+2 GF3 | Ranas 3 pts DG+1 GF1 | Sapos 0 pts DG-5 GF1
        List<Partido> partidos = List.of(
                partido(3, sapos, ranas, 0, 1, EstadoPartido.FINALIZADO),
                finalizado(1, pumas, 2, sapos, 0),
                finalizado(2, quetzales, 3, sapos, 1));

        List<PosicionEquipoResponse> tabla = calcular(List.of(pumas, quetzales, ranas, sapos), partidos);

        assertThat(tabla).extracting(PosicionEquipoResponse::equipo)
                .containsExactly("Quetzales", "Pumas", "Ranas", "Sapos");
        assertThat(tabla).extracting(PosicionEquipoResponse::pts).containsExactly(3, 3, 3, 0);
        assertThat(tabla).extracting(PosicionEquipoResponse::dg).containsExactly(2, 2, 1, -5);
        assertThat(tabla).extracting(PosicionEquipoResponse::gf).containsExactly(3, 2, 1, 1);
        assertThat(tabla).extracting(PosicionEquipoResponse::posicion).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Orden: si empatan puntos, diferencia y goles a favor, decide el nombre del equipo (no el id)")
    void orden_empateTotal_decideElNombre() {
        Equipo zeta = equipo(21, "Zeta");
        Equipo alfa = equipo(22, "Alfa");

        List<PosicionEquipoResponse> tabla = calcular(List.of(zeta, alfa), List.of(finalizado(1, zeta, 0, alfa, 0)));

        assertThat(tabla).extracting(PosicionEquipoResponse::equipo).containsExactly("Alfa", "Zeta");
    }

    @Test
    @DisplayName("Un equipo de la liga que aún no jugó aparece con ceros, ordenado por la diferencia de gol")
    void equipoSinPartidos_apareceConCeros() {
        Equipo e1 = equipo(1, "Uno");
        Equipo e2 = equipo(2, "Dos");
        Equipo e3 = equipo(3, "Tres");

        List<PosicionEquipoResponse> tabla = calcular(List.of(e1, e2, e3), List.of(finalizado(1, e1, 2, e2, 1)));

        // Uno 3 pts | Tres 0 pts DG 0 (sin jugar) | Dos 0 pts DG -1
        assertThat(tabla).containsExactly(
                new PosicionEquipoResponse(1, 1L, "Uno", 1, 1, 0, 0, 2, 1, 1, 3),
                new PosicionEquipoResponse(2, 3L, "Tres", 0, 0, 0, 0, 0, 0, 0, 0),
                new PosicionEquipoResponse(3, 2L, "Dos", 1, 0, 0, 1, 1, 2, -1, 0));
    }

    @Test
    @DisplayName("Una liga sin equipos ni partidos devuelve una tabla vacía")
    void ligaSinEquipos_devuelveTablaVacia() {
        assertThat(calcular(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("Un equipo que aparece en un partido finalizado pero no está inscrito en la liga también se cuenta")
    void equipoNoInscritoEnUnPartidoFinalizado_seCuenta() {
        Equipo inscrito = equipo(1, "Inscrito");
        Equipo visitanteAjeno = equipo(2, "Ajeno");

        List<PosicionEquipoResponse> tabla = calcular(List.of(inscrito),
                List.of(finalizado(1, inscrito, 0, visitanteAjeno, 1)));

        assertThat(tabla).extracting(PosicionEquipoResponse::equipo).containsExactly("Ajeno", "Inscrito");
        assertThat(tabla.get(0).pts()).isEqualTo(3);
    }

    // ------------------------------------------------------------------- 404

    @Test
    @DisplayName("Liga inexistente: RecursoNoEncontradoException (404) y no se consulta ningún partido")
    void ligaInexistente_lanza404() {
        when(ligaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.obtenerTabla(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Liga")
                .hasMessageContaining("99");

        verifyNoInteractions(equipoRepository, partidoRepository);
    }

    // ----------------------------------------------------------------- Apoyo

    private List<PosicionEquipoResponse> calcular(List<Equipo> equiposDeLaLiga, List<Partido> partidos) {
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(equipoRepository.buscarDeLiga(LIGA)).thenReturn(equiposDeLaLiga);
        when(partidoRepository.buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO)).thenReturn(partidos);
        return servicio.obtenerTabla(LIGA);
    }
}

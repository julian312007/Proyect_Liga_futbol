package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse.Resultado;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.liga;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.partidoDeLiga;
import static org.assertj.core.api.Assertions.assertThat;

/** Mapper del historial de un equipo: sin Spring ni base de datos. */
class HistorialPartidoMapperTest {

    private static final Instant FECHA = Instant.parse("2026-03-01T20:00:00Z");

    private final HistorialPartidoMapper mapper = new HistorialPartidoMapper();
    private final Liga liga = liga(7, "Liga Águila 2026");
    private final Equipo local = equipo(1, "Águilas");
    private final Equipo visitante = equipo(2, "Búhos");

    @Test
    @DisplayName("Copia los datos del partido y resume la liga y los equipos como {id, nombre}")
    void copiaLosDatosYResumeLasRelaciones() {
        Partido partido = partidoDeLiga(50, liga, 3, FECHA, local, visitante, 2, 1, EstadoPartido.FINALIZADO);

        PartidoHistorialResponse respuesta = mapper.toResponse(partido, 1L);

        assertThat(respuesta.id()).isEqualTo(50L);
        assertThat(respuesta.liga()).isEqualTo(new ResumenResponse(7L, "Liga Águila 2026"));
        assertThat(respuesta.jornada()).isEqualTo(3);
        assertThat(respuesta.fechaHora()).isEqualTo(FECHA);
        assertThat(respuesta.equipoLocal()).isEqualTo(new ResumenResponse(1L, "Águilas"));
        assertThat(respuesta.equipoVisitante()).isEqualTo(new ResumenResponse(2L, "Búhos"));
        assertThat(respuesta.golesLocal()).isEqualTo(2);
        assertThat(respuesta.golesVisitante()).isEqualTo(1);
        assertThat(respuesta.estado()).isEqualTo(EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("El equipo local que gana tiene esLocal=true y resultado VICTORIA")
    void localQueGana_esVictoria() {
        PartidoHistorialResponse respuesta = mapper.toResponse(
                partidoDeLiga(50, liga, 1, FECHA, local, visitante, 2, 1, EstadoPartido.FINALIZADO), 1L);

        assertThat(respuesta.esLocal()).isTrue();
        assertThat(respuesta.resultado()).isEqualTo(Resultado.VICTORIA);
    }

    @Test
    @DisplayName("El mismo partido visto desde el visitante es esLocal=false y resultado DERROTA")
    void visitanteQuePierde_esDerrota() {
        PartidoHistorialResponse respuesta = mapper.toResponse(
                partidoDeLiga(50, liga, 1, FECHA, local, visitante, 2, 1, EstadoPartido.FINALIZADO), 2L);

        assertThat(respuesta.esLocal()).isFalse();
        assertThat(respuesta.resultado()).isEqualTo(Resultado.DERROTA);
    }

    @Test
    @DisplayName("El visitante que gana tiene resultado VICTORIA y un empate es EMPATE para ambos")
    void visitanteQueGana_yEmpate() {
        Partido ganaElVisitante = partidoDeLiga(51, liga, 2, FECHA, local, visitante, 0, 3, EstadoPartido.FINALIZADO);
        Partido empate = partidoDeLiga(52, liga, 3, FECHA, local, visitante, 1, 1, EstadoPartido.FINALIZADO);

        assertThat(mapper.toResponse(ganaElVisitante, 2L).resultado()).isEqualTo(Resultado.VICTORIA);
        assertThat(mapper.toResponse(ganaElVisitante, 1L).resultado()).isEqualTo(Resultado.DERROTA);
        assertThat(mapper.toResponse(empate, 1L).resultado()).isEqualTo(Resultado.EMPATE);
        assertThat(mapper.toResponse(empate, 2L).resultado()).isEqualTo(Resultado.EMPATE);
    }

    @Test
    @DisplayName("Un partido que no está FINALIZADO no tiene resultado, aunque lleve goles")
    void partidoSinFinalizar_noTieneResultado() {
        for (EstadoPartido estado : new EstadoPartido[]{EstadoPartido.PROGRAMADO, EstadoPartido.EN_JUEGO}) {
            PartidoHistorialResponse respuesta = mapper.toResponse(
                    partidoDeLiga(53, liga, 4, FECHA, local, visitante, 1, 0, estado), 1L);

            assertThat(respuesta.resultado()).as("resultado de un partido %s", estado).isNull();
            assertThat(respuesta.estado()).isEqualTo(estado);
        }
    }
}

package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.support.FabricaEventosPartido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los mappers de la Fase 5 ({@link GolMapper}, {@link TarjetaMapper} y
 * {@link ResultadoPartidoMapper}) sin Spring: usan las implementaciones generadas por MapStruct.
 */
class EventosMapperTest {

    private final GolMapper golMapper = new GolMapperImpl();
    private final TarjetaMapper tarjetaMapper = new TarjetaMapperImpl();
    private final ResultadoPartidoMapper resultadoMapper = new ResultadoPartidoMapperImpl();

    private final Equipo local = FabricaEventosPartido.equipo(1L, "Local FC");
    private final Equipo visitante = FabricaEventosPartido.equipo(2L, "Visitante FC");
    private final Jugador luis = FabricaEventosPartido.jugador(101L, "Luis", local);
    private final Partido partido = FabricaEventosPartido.partido(50L, FabricaEventosPartido.liga(10L), local,
            visitante, EstadoPartido.EN_JUEGO, Instant.parse("2026-02-01T20:00:00Z"));

    @Test
    @DisplayName("GolMapper.toResponse: expone partidoId y resúmenes {id, nombre} del jugador y de su equipo")
    void gol_toResponse() {
        Gol gol = FabricaEventosPartido.gol(7L, partido, luis, 33, TipoGol.PENAL);

        GolResponse respuesta = golMapper.toResponse(gol);

        assertThat(respuesta.id()).isEqualTo(7L);
        assertThat(respuesta.partidoId()).isEqualTo(50L);
        assertThat(respuesta.jugador()).isEqualTo(new ResumenResponse(101L, "Luis"));
        assertThat(respuesta.equipo()).isEqualTo(new ResumenResponse(1L, "Local FC"));
        assertThat(respuesta.minuto()).isEqualTo(33);
        assertThat(respuesta.tipo()).isEqualTo(TipoGol.PENAL);
    }

    @Test
    @DisplayName("GolMapper.toEntity: copia minuto y tipo e ignora id y relaciones (las asigna el servicio)")
    void gol_toEntity() {
        Gol gol = golMapper.toEntity(new GolRequest(101L, 12, TipoGol.AUTOGOL));

        assertThat(gol.getId()).isNull();
        assertThat(gol.getPartido()).isNull();
        assertThat(gol.getJugador()).isNull();
        assertThat(gol.getMinuto()).isEqualTo(12);
        assertThat(gol.getTipo()).isEqualTo(TipoGol.AUTOGOL);
    }

    @Test
    @DisplayName("TarjetaMapper.toResponse: expone partidoId y resúmenes {id, nombre} del jugador y de su equipo")
    void tarjeta_toResponse() {
        Tarjeta tarjeta = FabricaEventosPartido.tarjeta(9L, partido, luis, TipoTarjeta.ROJA, 77);

        TarjetaResponse respuesta = tarjetaMapper.toResponse(tarjeta);

        assertThat(respuesta.id()).isEqualTo(9L);
        assertThat(respuesta.partidoId()).isEqualTo(50L);
        assertThat(respuesta.jugador()).isEqualTo(new ResumenResponse(101L, "Luis"));
        assertThat(respuesta.equipo()).isEqualTo(new ResumenResponse(1L, "Local FC"));
        assertThat(respuesta.tipo()).isEqualTo(TipoTarjeta.ROJA);
        assertThat(respuesta.minuto()).isEqualTo(77);
    }

    @Test
    @DisplayName("TarjetaMapper.toEntity: copia tipo y minuto e ignora id y relaciones")
    void tarjeta_toEntity() {
        Tarjeta tarjeta = tarjetaMapper.toEntity(new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 40));

        assertThat(tarjeta.getId()).isNull();
        assertThat(tarjeta.getPartido()).isNull();
        assertThat(tarjeta.getJugador()).isNull();
        assertThat(tarjeta.getTipo()).isEqualTo(TipoTarjeta.AMARILLA);
        assertThat(tarjeta.getMinuto()).isEqualTo(40);
    }

    @Test
    @DisplayName("Los mappers toleran relaciones nulas sin lanzar NullPointerException")
    void relacionesNulas() {
        Gol sinRelaciones = new Gol();
        sinRelaciones.setId(1L);

        GolResponse respuesta = golMapper.toResponse(sinRelaciones);

        assertThat(respuesta.partidoId()).isNull();
        assertThat(respuesta.jugador()).isNull();
        assertThat(respuesta.equipo()).isNull();
    }

    @Test
    @DisplayName("ResultadoPartidoMapper: partidoId, ligaId, marcador y estado")
    void resultado_toResponse() {
        partido.setGolesLocal(2);
        partido.setGolesVisitante(1);
        partido.setEstado(EstadoPartido.FINALIZADO);

        ResultadoPartidoResponse respuesta = resultadoMapper.toResponse(partido);

        assertThat(respuesta).isEqualTo(new ResultadoPartidoResponse(50L, 10L, 2, 1, EstadoPartido.FINALIZADO));
    }
}

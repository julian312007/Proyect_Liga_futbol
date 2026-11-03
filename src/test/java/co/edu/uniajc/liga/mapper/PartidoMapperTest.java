package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de {@link PartidoMapper} sin Spring: es la implementación generada por MapStruct, instanciada a mano. */
class PartidoMapperTest {

    private static final Instant HORA = Instant.parse("2026-02-01T20:00:00-05:00");

    private final PartidoMapper mapper = new PartidoMapperImpl();

    @Test
    @DisplayName("toEntity copia jornada y fecha y deja sin asignar id, relaciones, marcador y estado")
    void toEntity_copiaSoloJornadaYFecha() {
        PartidoRequest solicitud = new PartidoRequest(1L, 3, HORA, 10L, 11L, 20L, 30L);

        Partido partido = mapper.toEntity(solicitud);

        assertThat(partido.getJornada()).isEqualTo(3);
        assertThat(partido.getFechaHora()).isEqualTo(HORA);
        assertThat(partido.getId()).isNull();
        assertThat(partido.getLiga()).isNull();
        assertThat(partido.getEquipoLocal()).isNull();
        assertThat(partido.getEquipoVisitante()).isNull();
        assertThat(partido.getEstadio()).isNull();
        assertThat(partido.getArbitro()).isNull();
        // Valores por defecto de la entidad: un partido nace PROGRAMADO con marcador 0-0.
        assertThat(partido.getGolesLocal()).isZero();
        assertThat(partido.getGolesVisitante()).isZero();
        assertThat(partido.getEstado()).isEqualTo(EstadoPartido.PROGRAMADO);
    }

    @Test
    @DisplayName("toResponse resume cada relación como {id, nombre} y copia marcador y estado")
    void toResponse_resumeLasRelaciones() {
        Partido partido = partidoCompleto();

        PartidoResponse respuesta = mapper.toResponse(partido);

        assertThat(respuesta.id()).isEqualTo(7L);
        assertThat(respuesta.jornada()).isEqualTo(3);
        assertThat(respuesta.fechaHora()).isEqualTo(HORA);
        assertThat(respuesta.liga().id()).isEqualTo(1L);
        assertThat(respuesta.liga().nombre()).isEqualTo("Liga Betplay");
        assertThat(respuesta.equipoLocal().id()).isEqualTo(10L);
        assertThat(respuesta.equipoLocal().nombre()).isEqualTo("Cali FC");
        assertThat(respuesta.equipoVisitante().id()).isEqualTo(11L);
        assertThat(respuesta.equipoVisitante().nombre()).isEqualTo("Medellín FC");
        assertThat(respuesta.estadio().id()).isEqualTo(20L);
        assertThat(respuesta.estadio().nombre()).isEqualTo("Pascual Guerrero");
        assertThat(respuesta.arbitro().id()).isEqualTo(30L);
        assertThat(respuesta.arbitro().nombre()).isEqualTo("Wilmar Roldán");
        assertThat(respuesta.golesLocal()).isEqualTo(2);
        assertThat(respuesta.golesVisitante()).isEqualTo(1);
        assertThat(respuesta.estado()).isEqualTo(EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("toResponse tolera relaciones nulas sin lanzar excepción")
    void toResponse_relacionesNulas() {
        Partido partido = new Partido();
        partido.setId(1L);

        PartidoResponse respuesta = mapper.toResponse(partido);

        assertThat(respuesta.liga()).isNull();
        assertThat(respuesta.equipoLocal()).isNull();
        assertThat(respuesta.estadio()).isNull();
        assertThat(respuesta.arbitro()).isNull();
    }

    @Test
    @DisplayName("updateEntity cambia jornada y fecha, y no toca id, relaciones, marcador ni estado")
    void updateEntity_noTocaLoQueNoDebe() {
        Partido existente = partidoCompleto();
        PartidoRequest solicitud = new PartidoRequest(99L, 8, HORA.plusSeconds(3600), 98L, 97L, 96L, 95L);

        mapper.updateEntity(solicitud, existente);

        assertThat(existente.getJornada()).isEqualTo(8);
        assertThat(existente.getFechaHora()).isEqualTo(HORA.plusSeconds(3600));
        assertThat(existente.getId()).isEqualTo(7L);
        assertThat(existente.getLiga().getId()).isEqualTo(1L);
        assertThat(existente.getEquipoLocal().getId()).isEqualTo(10L);
        assertThat(existente.getEquipoVisitante().getId()).isEqualTo(11L);
        assertThat(existente.getEstadio().getId()).isEqualTo(20L);
        assertThat(existente.getArbitro().getId()).isEqualTo(30L);
        assertThat(existente.getGolesLocal()).isEqualTo(2);
        assertThat(existente.getGolesVisitante()).isEqualTo(1);
        assertThat(existente.getEstado()).isEqualTo(EstadoPartido.FINALIZADO);
    }

    private static Partido partidoCompleto() {
        Liga liga = new Liga();
        liga.setId(1L);
        liga.setNombre("Liga Betplay");
        Equipo local = new Equipo();
        local.setId(10L);
        local.setNombre("Cali FC");
        Equipo visitante = new Equipo();
        visitante.setId(11L);
        visitante.setNombre("Medellín FC");
        Estadio estadio = new Estadio();
        estadio.setId(20L);
        estadio.setNombre("Pascual Guerrero");
        Arbitro arbitro = new Arbitro();
        arbitro.setId(30L);
        arbitro.setNombre("Wilmar Roldán");

        Partido partido = new Partido();
        partido.setId(7L);
        partido.setLiga(liga);
        partido.setJornada(3);
        partido.setFechaHora(HORA);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setEstadio(estadio);
        partido.setArbitro(arbitro);
        partido.setGolesLocal(2);
        partido.setGolesVisitante(1);
        partido.setEstado(EstadoPartido.FINALIZADO);
        return partido;
    }
}

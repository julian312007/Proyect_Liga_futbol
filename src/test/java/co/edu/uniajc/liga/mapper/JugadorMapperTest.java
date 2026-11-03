package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.PosicionJugador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas de {@link JugadorMapper} sin Spring: se instancia la implementación que genera MapStruct. */
class JugadorMapperTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(2000, 5, 17);

    private final JugadorMapper mapper = new JugadorMapperImpl();

    @Test
    @DisplayName("toEntity copia los campos simples e ignora el id y el equipo (lo resuelve el servicio)")
    void toEntity_copiaCamposSimples_ignoraIdYEquipo() {
        Jugador jugador = mapper.toEntity(new JugadorRequest("Juan Perez", NACIMIENTO, PosicionJugador.PORTERO, 1, 10L));

        assertThat(jugador.getId()).isNull();
        assertThat(jugador.getNombre()).isEqualTo("Juan Perez");
        assertThat(jugador.getFechaNacimiento()).isEqualTo(NACIMIENTO);
        assertThat(jugador.getPosicion()).isEqualTo(PosicionJugador.PORTERO);
        assertThat(jugador.getDorsal()).isEqualTo(1);
        assertThat(jugador.getEquipo()).isNull();
    }

    @Test
    @DisplayName("toResponse muestra el equipo como resumen {id, nombre}")
    void toResponse_equipoComoResumen() {
        JugadorResponse respuesta = mapper.toResponse(jugadorConEquipo());

        assertThat(respuesta.id()).isEqualTo(100L);
        assertThat(respuesta.nombre()).isEqualTo("Juan Perez");
        assertThat(respuesta.fechaNacimiento()).isEqualTo(NACIMIENTO);
        assertThat(respuesta.posicion()).isEqualTo(PosicionJugador.DEFENSA);
        assertThat(respuesta.dorsal()).isEqualTo(4);
        assertThat(respuesta.equipo().id()).isEqualTo(10L);
        assertThat(respuesta.equipo().nombre()).isEqualTo("Deportivo Cali");
    }

    @Test
    @DisplayName("toResponse sin equipo no falla: el resumen es nulo; y de null devuelve null")
    void toResponse_sinEquipoONulo() {
        Jugador jugador = new Jugador();
        jugador.setId(100L);

        assertThat(mapper.toResponse(jugador).equipo()).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("updateEntity actualiza los campos simples y no toca el id ni el equipo")
    void updateEntity_noTocaIdNiEquipo() {
        Jugador jugador = jugadorConEquipo();

        mapper.updateEntity(new JugadorRequest("Pedro Gomez", LocalDate.of(1999, 1, 2), PosicionJugador.DELANTERO, 11, 99L),
                jugador);

        assertThat(jugador.getId()).isEqualTo(100L);
        assertThat(jugador.getNombre()).isEqualTo("Pedro Gomez");
        assertThat(jugador.getFechaNacimiento()).isEqualTo(LocalDate.of(1999, 1, 2));
        assertThat(jugador.getPosicion()).isEqualTo(PosicionJugador.DELANTERO);
        assertThat(jugador.getDorsal()).isEqualTo(11);
        assertThat(jugador.getEquipo().getId()).isEqualTo(10L);
    }

    private static Jugador jugadorConEquipo() {
        Equipo equipo = new Equipo();
        equipo.setId(10L);
        equipo.setNombre("Deportivo Cali");

        Jugador jugador = new Jugador();
        jugador.setId(100L);
        jugador.setNombre("Juan Perez");
        jugador.setFechaNacimiento(NACIMIENTO);
        jugador.setPosicion(PosicionJugador.DEFENSA);
        jugador.setDorsal(4);
        jugador.setEquipo(equipo);
        return jugador;
    }
}

package co.edu.uniajc.liga.support;

import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.entity.TipoTarjeta;

import java.time.Instant;

/**
 * Fábrica de entidades en memoria (sin base de datos) para las pruebas unitarias de la Fase 5: equipos,
 * jugadores, partidos, goles y tarjetas con ids ya asignados. No usa Spring ni mocks.
 */
public final class FabricaEventosPartido {

    private FabricaEventosPartido() {
    }

    public static Liga liga(Long id) {
        Liga liga = new Liga();
        liga.setId(id);
        liga.setNombre("Liga " + id);
        return liga;
    }

    public static Equipo equipo(Long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

    public static Jugador jugador(Long id, String nombre, Equipo equipo) {
        Jugador jugador = new Jugador();
        jugador.setId(id);
        jugador.setNombre(nombre);
        jugador.setEquipo(equipo);
        return jugador;
    }

    public static Partido partido(Long id, Liga liga, Equipo local, Equipo visitante, EstadoPartido estado,
                                  Instant fechaHora) {
        Partido partido = new Partido();
        partido.setId(id);
        partido.setLiga(liga);
        partido.setJornada(1);
        partido.setFechaHora(fechaHora);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setEstado(estado);
        return partido;
    }

    public static Gol gol(Long id, Partido partido, Jugador jugador, int minuto, TipoGol tipo) {
        Gol gol = new Gol();
        gol.setId(id);
        gol.setPartido(partido);
        gol.setJugador(jugador);
        gol.setMinuto(minuto);
        gol.setTipo(tipo);
        return gol;
    }

    public static Tarjeta tarjeta(Long id, Partido partido, Jugador jugador, TipoTarjeta tipo, int minuto) {
        Tarjeta tarjeta = new Tarjeta();
        tarjeta.setId(id);
        tarjeta.setPartido(partido);
        tarjeta.setJugador(jugador);
        tarjeta.setTipo(tipo);
        tarjeta.setMinuto(minuto);
        return tarjeta;
    }
}

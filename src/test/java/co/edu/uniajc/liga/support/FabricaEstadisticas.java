package co.edu.uniajc.liga.support;

import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.entity.TipoTarjeta;

import java.time.Instant;

/**
 * Arma entidades en memoria (con id) para las pruebas de las estadísticas de la Fase 6, que no usan base de
 * datos. Las entidades solo tienen getters y setters, así que aquí se fijan a mano.
 */
public final class FabricaEstadisticas {

    private FabricaEstadisticas() {
    }

    public static Liga liga(long id, String nombre) {
        Liga liga = new Liga();
        liga.setId(id);
        liga.setNombre(nombre);
        return liga;
    }

    public static Equipo equipo(long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

    public static Jugador jugador(long id, String nombre, Equipo equipo) {
        Jugador jugador = new Jugador();
        jugador.setId(id);
        jugador.setNombre(nombre);
        jugador.setEquipo(equipo);
        return jugador;
    }

    /** Partido sin liga ni fecha: lo que necesita la tabla de posiciones. */
    public static Partido partido(long id, Equipo local, Equipo visitante, int golesLocal, int golesVisitante,
                                  EstadoPartido estado) {
        Partido partido = new Partido();
        partido.setId(id);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setGolesLocal(golesLocal);
        partido.setGolesVisitante(golesVisitante);
        partido.setEstado(estado);
        return partido;
    }

    /** Partido finalizado. */
    public static Partido finalizado(long id, Equipo local, int golesLocal, Equipo visitante, int golesVisitante) {
        return partido(id, local, visitante, golesLocal, golesVisitante, EstadoPartido.FINALIZADO);
    }

    /** Partido completo, como lo devuelve la consulta del historial (con liga, jornada y fecha). */
    public static Partido partidoDeLiga(long id, Liga liga, int jornada, Instant fechaHora, Equipo local,
                                        Equipo visitante, int golesLocal, int golesVisitante, EstadoPartido estado) {
        Partido partido = partido(id, local, visitante, golesLocal, golesVisitante, estado);
        partido.setLiga(liga);
        partido.setJornada(jornada);
        partido.setFechaHora(fechaHora);
        return partido;
    }

    public static Gol gol(long id, Jugador jugador, TipoGol tipo) {
        Gol gol = new Gol();
        gol.setId(id);
        gol.setJugador(jugador);
        gol.setTipo(tipo);
        return gol;
    }

    public static Tarjeta tarjeta(long id, Jugador jugador, TipoTarjeta tipo) {
        Tarjeta tarjeta = new Tarjeta();
        tarjeta.setId(id);
        tarjeta.setJugador(jugador);
        tarjeta.setTipo(tipo);
        return tarjeta;
    }
}

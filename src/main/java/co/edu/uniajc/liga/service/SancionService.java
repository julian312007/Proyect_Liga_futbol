package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.response.SancionJugadorResponse;
import co.edu.uniajc.liga.dto.response.SuspendidoResponse;

import java.util.List;

/** Sanciones de una liga (F7): jugadores con tarjetas y suspendidos de una jornada (RN-10). */
public interface SancionService {

    /**
     * Jugadores de la liga con al menos una tarjeta, con su conteo de amarillas y rojas. Cuenta las tarjetas de
     * todos los partidos de la liga (esta consulta no se guarda en caché).
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException si la liga no existe
     */
    List<SancionJugadorResponse> listarSanciones(Long ligaId);

    /**
     * Jugadores suspendidos en algún partido de la jornada, según {@link SuspensionService} (RN-10). Lista
     * vacía si la jornada no tiene partidos o ninguno de sus jugadores está suspendido.
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException si la liga no existe
     */
    List<SuspendidoResponse> listarSuspendidos(Long ligaId, Integer jornada);
}

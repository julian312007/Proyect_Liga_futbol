package co.edu.uniajc.liga.dto.response;

/**
 * Jugador suspendido para un partido por una expulsión en el partido anterior de su equipo (RN-10).
 *
 * @param jugadorId     id del jugador suspendido
 * @param jugador       nombre del jugador
 * @param equipoId      id de su equipo
 * @param equipo        nombre de su equipo
 * @param partidoRojaId id del partido en el que recibió la roja
 * @param jornadaRoja   jornada de ese partido
 */
public record SuspendidoResponse(Long jugadorId, String jugador, Long equipoId, String equipo,
                                 Long partidoRojaId, Integer jornadaRoja) {
}

package co.edu.uniajc.liga.dto.response;

/**
 * Jugador con tarjetas en una liga (F7), con el conteo de amarillas y de rojas. Solo aparecen los jugadores
 * que tienen al menos una tarjeta.
 *
 * @param jugadorId id del jugador
 * @param jugador   nombre del jugador
 * @param equipoId  id del equipo actual del jugador
 * @param equipo    nombre del equipo actual del jugador
 * @param amarillas cantidad de tarjetas amarillas
 * @param rojas     cantidad de tarjetas rojas
 */
public record SancionJugadorResponse(Long jugadorId, String jugador, Long equipoId, String equipo,
                                     int amarillas, int rojas) {
}

package co.edu.uniajc.liga.dto.response;

/**
 * Fila de la tabla de goleadores de una liga (F5). Suman los goles {@code NORMAL} y {@code PENAL}; el
 * {@code AUTOGOL} no. Solo cuenta partidos {@code FINALIZADO}. Se cachea en Redis como JSON (D-3).
 *
 * @param posicion  lugar en la tabla, desde 1 (orden: goles, de mayor a menor, y luego nombre)
 * @param jugadorId id del jugador
 * @param jugador   nombre del jugador
 * @param equipoId  id del equipo actual del jugador
 * @param equipo    nombre del equipo actual del jugador
 * @param goles     goles que suman a su cuenta
 */
public record GoleadorResponse(int posicion, Long jugadorId, String jugador, Long equipoId, String equipo,
                               int goles) {
}

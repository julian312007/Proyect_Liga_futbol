package co.edu.uniajc.liga.dto.response;

/**
 * Fila de la tabla de posiciones de una liga (F4). Solo cuenta partidos {@code FINALIZADO}.
 *
 * <p>Los nombres de los contadores son los del documento del proyecto: PJ, PG, PE, PP, GF, GC, DG y Pts.
 * Se cachea en Redis como JSON (D-3), por eso es un {@code record} sin entidades ni relaciones.
 *
 * @param posicion lugar en la tabla, desde 1 (orden: puntos, diferencia de gol y goles a favor)
 * @param equipoId id del equipo
 * @param equipo   nombre del equipo
 * @param pj       partidos jugados
 * @param pg       partidos ganados
 * @param pe       partidos empatados
 * @param pp       partidos perdidos
 * @param gf       goles a favor
 * @param gc       goles en contra
 * @param dg       diferencia de gol ({@code gf - gc})
 * @param pts      puntos (RN-01: victoria 3, empate 1, derrota 0)
 */
public record PosicionEquipoResponse(int posicion, Long equipoId, String equipo, int pj, int pg, int pe, int pp,
                                     int gf, int gc, int dg, int pts) {
}

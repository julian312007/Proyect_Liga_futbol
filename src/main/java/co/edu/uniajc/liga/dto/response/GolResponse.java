package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.TipoGol;

/**
 * Gol de un partido.
 *
 * @param id        id del gol
 * @param partidoId id del partido
 * @param jugador   jugador que marca (en un autogol, quien lo marca en contra)
 * @param equipo    equipo del jugador (no necesariamente el equipo al que suma el gol si es un autogol)
 * @param minuto    minuto del gol
 * @param tipo      {@code NORMAL}, {@code PENAL} o {@code AUTOGOL}
 */
public record GolResponse(Long id, Long partidoId, ResumenResponse jugador, ResumenResponse equipo,
                          Integer minuto, TipoGol tipo) {
}

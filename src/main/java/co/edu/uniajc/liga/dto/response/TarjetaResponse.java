package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.TipoTarjeta;

/**
 * Tarjeta de un partido.
 *
 * @param id        id de la tarjeta
 * @param partidoId id del partido
 * @param jugador   jugador sancionado
 * @param equipo    equipo del jugador
 * @param tipo      {@code AMARILLA} o {@code ROJA}
 * @param minuto    minuto de la tarjeta
 */
public record TarjetaResponse(Long id, Long partidoId, ResumenResponse jugador, ResumenResponse equipo,
                              TipoTarjeta tipo, Integer minuto) {
}

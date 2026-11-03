package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.EstadoPartido;

/**
 * Resultado de finalizar un partido ({@code POST /api/v1/partidos/{partidoId}/finalizar}).
 *
 * <p>Incluye el id de la liga porque es la clave de las cachés de la tabla de posiciones y de goleadores
 * que se invalidan al finalizar.
 *
 * @param partidoId      id del partido
 * @param ligaId         id de la liga del partido
 * @param golesLocal     goles del local ya confirmados
 * @param golesVisitante goles del visitante ya confirmados
 * @param estado         siempre {@code FINALIZADO} tras una finalización correcta
 */
public record ResultadoPartidoResponse(Long partidoId, Long ligaId, Integer golesLocal, Integer golesVisitante,
                                       EstadoPartido estado) {
}

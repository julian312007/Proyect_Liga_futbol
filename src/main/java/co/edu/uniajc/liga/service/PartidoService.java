package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import org.springframework.data.domain.Pageable;

/**
 * Programación de partidos y calendario (F2 y F8). Aquí viven las reglas RN-04 a RN-07 y la parte de RN-12 que
 * corresponde al partido.
 *
 * <p>Los errores se comunican con excepciones de dominio; {@code GlobalExceptionHandler} las traduce:
 * <ul>
 *   <li>{@code RecursoNoEncontradoException} (404): el partido, la liga, un equipo, el estadio o el árbitro
 *       no existen.</li>
 *   <li>{@code ReglaNegocioException} (422): RN-04, un equipo no puede enfrentarse a sí mismo.</li>
 *   <li>{@code ConflictoException} (409): RN-05, RN-06 y RN-07 (choques), RN-12 y {@code PARTIDO_EN_JUEGO}
 *       (modificar o borrar un partido finalizado o en juego) y {@code PARTIDO_ESTADO_INVALIDO} (iniciar un
 *       partido que no está programado).</li>
 * </ul>
 */
public interface PartidoService {

    /** Programa un partido: queda {@code PROGRAMADO} con marcador 0-0. Aplica RN-04, RN-05, RN-06 y RN-07. */
    PartidoResponse crear(PartidoRequest request);

    /**
     * Calendario paginado. Todos los filtros son opcionales y se combinan con "y".
     *
     * @param equipoId partidos del equipo como local o como visitante
     */
    PaginaResponse<PartidoResponse> listar(Long ligaId, Integer jornada, Long equipoId, Long estadioId,
                                           Pageable pageable);

    PartidoResponse obtener(Long id);

    /** Reprograma un partido {@code PROGRAMADO}; vuelve a aplicar RN-04 a RN-07 excluyendo al propio partido. */
    PartidoResponse actualizar(Long id, PartidoRequest request);

    /** Elimina un partido {@code PROGRAMADO}. Un partido en juego o finalizado no se elimina (409). */
    void eliminar(Long id);

    /** Pasa un partido de {@code PROGRAMADO} a {@code EN_JUEGO}; desde cualquier otro estado responde 409. */
    PartidoResponse iniciar(Long id);
}

package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Goles de un partido (F3; RN-08, RN-10 y RN-12).
 *
 * <p>Errores: {@code RecursoNoEncontradoException} (404) si el partido, el jugador o el gol no existen;
 * {@code ConflictoException} (409) si el partido no está {@code EN_JUEGO} (código {@code RN-12} si ya está
 * {@code FINALIZADO}, {@code PARTIDO_NO_EN_JUEGO} si aún está {@code PROGRAMADO});
 * {@code ReglaNegocioException} (422) por RN-08 o RN-10.
 */
public interface GolService {

    /**
     * Registra un gol. El partido debe estar {@code EN_JUEGO}; el jugador debe ser de uno de los dos equipos
     * (RN-08) y no estar suspendido en el partido (RN-10).
     */
    GolResponse registrar(Long partidoId, GolRequest request);

    /** Goles del partido, paginados (orden por defecto: minuto y luego id). 404 si el partido no existe. */
    PaginaResponse<GolResponse> listar(Long partidoId, Pageable pageable);

    /** Elimina un gol del partido. El partido debe estar {@code EN_JUEGO} (un partido finalizado no cambia, RN-12). */
    void eliminar(Long partidoId, Long golId);
}

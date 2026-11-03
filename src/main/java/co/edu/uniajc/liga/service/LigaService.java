package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Catálogo de ligas (F1 parcial). Reglas:
 * <ul>
 *   <li>Nombre y temporada obligatorios ({@code LigaRequest}, 400).</li>
 *   <li>{@code fechaFin} posterior a {@code fechaInicio}: si no, {@code ReglaNegocioException} con código
 *       {@code LIGA_FECHAS_INVALIDAS} (422).</li>
 *   <li>Único (nombre, temporada): si no, {@code ConflictoException} con código {@code LIGA_DUPLICADA} (409).</li>
 *   <li>Una liga con equipos o partidos no se elimina (409): lo deciden los {@link VerificadorEliminacion} de
 *       las fases posteriores. Al eliminarla se invalidan sus cachés de estadísticas.</li>
 * </ul>
 * Una liga inexistente lanza {@code RecursoNoEncontradoException} (404).
 */
public interface LigaService {

    /** Página de ligas; el tamaño y el orden se normalizan con {@link PaginacionSegura}. */
    PaginaResponse<LigaResponse> listar(Pageable pageable);

    LigaResponse obtener(Long id);

    LigaResponse crear(LigaRequest request);

    /** Reemplaza los datos de la liga. La unicidad excluye a la propia liga. */
    LigaResponse actualizar(Long id, LigaRequest request);

    /** 409 si algún {@link VerificadorEliminacion} detecta dependientes. */
    void eliminar(Long id);
}

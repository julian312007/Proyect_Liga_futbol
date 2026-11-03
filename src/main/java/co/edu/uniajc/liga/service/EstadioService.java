package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Catálogo de estadios (F1 parcial) y controlador independiente de referencia (D-4). Aquí viven las reglas:
 * <ul>
 *   <li><b>EST-01</b>: el nombre se normaliza y es único por ciudad sin distinguir mayúsculas; al crear y
 *       al actualizar (excluyendo al propio estadio). Conflicto: {@code ConflictoException("EST-01")}, 409.</li>
 *   <li><b>EST-02</b>: no se elimina un estadio con dependientes (equipos que lo tienen de local, partidos).
 *       Se consulta a los {@link VerificadorEliminacion} de las fases posteriores; el conflicto es 409.</li>
 *   <li><b>EST-03</b> (capacidad mayor que cero, ciudad obligatoria) se valida en {@code EstadioRequest}: 400.</li>
 * </ul>
 * Un estadio inexistente lanza {@code RecursoNoEncontradoException} (404).
 */
public interface EstadioService {

    /** Página de estadios; el tamaño y el orden se normalizan con {@link PaginacionSegura}. */
    PaginaResponse<EstadioResponse> listar(Pageable pageable);

    EstadioResponse obtener(Long id);

    /** EST-01. */
    EstadioResponse crear(EstadioRequest request);

    /** Reemplaza los datos del estadio. EST-01 excluyendo al propio registro. */
    EstadioResponse actualizar(Long id, EstadioRequest request);

    /** EST-02. */
    void eliminar(Long id);
}

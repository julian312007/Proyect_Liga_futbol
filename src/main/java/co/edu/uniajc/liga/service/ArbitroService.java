package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Catálogo de árbitros (F1 parcial). Reglas: nombre obligatorio y años de experiencia de cero o más (ambas
 * en {@code ArbitroRequest}, 400); un árbitro con partidos asignados no se elimina (409): lo decide un
 * {@link VerificadorEliminacion} de la fase de partidos. Un árbitro inexistente da 404.
 */
public interface ArbitroService {

    /** Página de árbitros; el tamaño y el orden se normalizan con {@link PaginacionSegura}. */
    PaginaResponse<ArbitroResponse> listar(Pageable pageable);

    ArbitroResponse obtener(Long id);

    ArbitroResponse crear(ArbitroRequest request);

    /** Reemplaza los datos del árbitro. */
    ArbitroResponse actualizar(Long id, ArbitroRequest request);

    /** 409 si algún {@link VerificadorEliminacion} detecta dependientes. */
    void eliminar(Long id);
}

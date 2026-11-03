package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Catálogo de entrenadores (F1 parcial). Regla: nombre obligatorio ({@code EntrenadorRequest}, 400). Un
 * entrenador asignado a un equipo no se elimina (409): lo decide un {@link VerificadorEliminacion} de la fase
 * de equipos. Un entrenador inexistente da 404.
 */
public interface EntrenadorService {

    /** Página de entrenadores; el tamaño y el orden se normalizan con {@link PaginacionSegura}. */
    PaginaResponse<EntrenadorResponse> listar(Pageable pageable);

    EntrenadorResponse obtener(Long id);

    EntrenadorResponse crear(EntrenadorRequest request);

    /** Reemplaza los datos del entrenador. */
    EntrenadorResponse actualizar(Long id, EntrenadorRequest request);

    /** 409 si algún {@link VerificadorEliminacion} detecta dependientes. */
    void eliminar(Long id);
}

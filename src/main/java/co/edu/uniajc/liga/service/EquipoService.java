package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Casos de uso de los equipos (F1 y F6). Aquí viven RN-03 (un entrenador dirige a un solo equipo), la unicidad
 * (liga, nombre) y el año de fundación no futuro. No conoce HTTP: comunica los errores con excepciones de
 * dominio.
 *
 * <ul>
 *   <li>{@code RecursoNoEncontradoException} (404): el equipo, la liga, el estadio o el entrenador no existen.</li>
 *   <li>{@code ConflictoException} (409): nombre repetido en la liga ({@code EQUIPO_DUPLICADO}), entrenador ya
 *       asignado a otro equipo ({@code RN-03}) o equipo con dependientes ({@code CONFLICTO_DEPENDENCIAS}).</li>
 *   <li>{@code ReglaNegocioException} (422): año de fundación posterior al actual ({@code ANIO_FUNDACION_FUTURO}).</li>
 * </ul>
 */
public interface EquipoService {

    /** Equipos paginados. Solo se acepta ordenar por id, nombre, ciudad o anioFundacion. */
    PaginaResponse<EquipoResponse> listar(Pageable pageable);

    EquipoResponse obtener(Long id);

    EquipoResponse crear(EquipoRequest request);

    /** Reemplaza los datos del equipo. Excluye al propio equipo al comprobar nombre y entrenador (RN-03). */
    EquipoResponse actualizar(Long id, EquipoRequest request);

    /**
     * Elimina el equipo. 409 si tiene jugadores o si algún verificador de otra fase (por ejemplo, partidos)
     * detecta dependientes.
     */
    void eliminar(Long id);

    /** Jugadores del equipo (F6), paginados. 404 si el equipo no existe. */
    PaginaResponse<JugadorResponse> plantel(Long equipoId, Pageable pageable);

    /** Cuerpo técnico del equipo (F6): su entrenador. 404 si el equipo no existe. */
    CuerpoTecnicoResponse cuerpoTecnico(Long equipoId);
}

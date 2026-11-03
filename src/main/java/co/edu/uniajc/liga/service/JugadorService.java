package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Casos de uso de los jugadores (F1 y F6). Aquí viven RN-02 (un jugador pertenece a un solo equipo: al cambiar
 * de equipo se mueve, no se duplica), la unicidad (equipo, dorsal) y la fecha de nacimiento en el pasado.
 *
 * <ul>
 *   <li>{@code RecursoNoEncontradoException} (404): el jugador o el equipo no existen.</li>
 *   <li>{@code ConflictoException} (409): dorsal ocupado en el equipo ({@code DORSAL_DUPLICADO}) o dependientes
 *       detectados por otra fase al borrar ({@code CONFLICTO_DEPENDENCIAS}).</li>
 *   <li>{@code ReglaNegocioException} (422): fecha de nacimiento no anterior a hoy
 *       ({@code FECHA_NACIMIENTO_FUTURA}).</li>
 * </ul>
 */
public interface JugadorService {

    /**
     * Jugadores paginados; con {@code equipoId} solo los de ese equipo (404 si el equipo no existe). Solo se
     * acepta ordenar por id, nombre, dorsal, posicion o fechaNacimiento.
     */
    PaginaResponse<JugadorResponse> listar(Long equipoId, Pageable pageable);

    /** Plantel de un equipo, paginado; por defecto ordenado por dorsal. 404 si el equipo no existe. */
    PaginaResponse<JugadorResponse> plantel(Long equipoId, Pageable pageable);

    JugadorResponse obtener(Long id);

    JugadorResponse crear(JugadorRequest request);

    /**
     * Reemplaza los datos del jugador. Si cambia {@code equipoId}, el mismo jugador pasa al equipo destino
     * (RN-02), donde el dorsal debe estar libre.
     */
    JugadorResponse actualizar(Long id, JugadorRequest request);

    /** Elimina al jugador salvo que un verificador de otra fase (por ejemplo, goles o tarjetas) lo impida. */
    void eliminar(Long id);
}

package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import org.springframework.data.domain.Pageable;

/** Historial de partidos de un equipo (F6). */
public interface HistorialEquipoService {

    /**
     * Partidos del equipo, de local o de visitante y en cualquier estado, paginados. Por defecto los más
     * recientes primero; solo se acepta ordenar por {@code fechaHora}, {@code jornada} o {@code id}.
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException si el equipo no existe
     */
    PaginaResponse<PartidoHistorialResponse> obtenerHistorial(Long equipoId, Pageable pageable);
}

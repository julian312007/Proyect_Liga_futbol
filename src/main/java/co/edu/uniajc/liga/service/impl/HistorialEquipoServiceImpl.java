package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.HistorialPartidoMapper;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.service.HistorialEquipoService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Historial de partidos de un equipo (F6). No se guarda en caché: es una consulta paginada que incluye
 * partidos aún sin terminar.
 */
@Service
public class HistorialEquipoServiceImpl implements HistorialEquipoService {

    /** Propiedades de {@code Partido} por las que se acepta ordenar. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("fechaHora", "jornada", "id");

    /** Los partidos más recientes primero. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id"));

    private final EquipoRepository equipoRepository;
    private final EstadisticasPartidoRepository partidoRepository;
    private final HistorialPartidoMapper mapper;

    public HistorialEquipoServiceImpl(EquipoRepository equipoRepository,
                                      EstadisticasPartidoRepository partidoRepository,
                                      HistorialPartidoMapper mapper) {
        this.equipoRepository = equipoRepository;
        this.partidoRepository = partidoRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PartidoHistorialResponse> obtenerHistorial(Long equipoId, Pageable pageable) {
        if (!equipoRepository.existsById(equipoId)) {
            throw new RecursoNoEncontradoException("Equipo", equipoId);
        }
        Pageable segura = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        // El mapeo ocurre dentro de la transacción: la consulta ya trajo liga y equipos, no hay lazy loading.
        return PaginaResponse.de(partidoRepository.buscarHistorialDeEquipo(equipoId, segura)
                .map(partido -> mapper.toResponse(partido, equipoId)));
    }
}

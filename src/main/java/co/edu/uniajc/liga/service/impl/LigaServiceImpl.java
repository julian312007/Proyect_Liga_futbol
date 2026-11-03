package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.LigaMapper;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.LigaService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Implementación de {@link LigaService}: fechas coherentes (422), unicidad (nombre, temporada) (409) y
 * verificación de dependientes antes de borrar.
 */
@Service
public class LigaServiceImpl implements LigaService {

    /** Código de la regla "fechaFin posterior a fechaInicio" (422). */
    static final String CODIGO_FECHAS_INVALIDAS = "LIGA_FECHAS_INVALIDAS";

    /** Código del conflicto por (nombre, temporada) repetidos (409). */
    static final String CODIGO_LIGA_DUPLICADA = "LIGA_DUPLICADA";

    /** Propiedades de {@link Liga} por las que se acepta ordenar un listado. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "temporada", "fechaInicio", "fechaFin");

    /** Orden si el cliente no pide uno válido: las más recientes primero y, para estabilidad, por id. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.asc("id"));

    private final LigaRepository ligaRepository;
    private final LigaMapper ligaMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;

    public LigaServiceImpl(LigaRepository ligaRepository,
                           LigaMapper ligaMapper,
                           VerificadoresEliminacion verificadoresEliminacion) {
        this.ligaRepository = ligaRepository;
        this.ligaMapper = ligaMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<LigaResponse> listar(Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(ligaRepository.findAll(seguro).map(ligaMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public LigaResponse obtener(Long id) {
        return ligaMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public LigaResponse crear(LigaRequest request) {
        String nombre = TextoCatalogo.normalizarObligatorio(request.nombre(), "nombre");
        String temporada = TextoCatalogo.normalizarObligatorio(request.temporada(), "temporada");
        validarFechas(request.fechaInicio(), request.fechaFin());
        if (ligaRepository.existsByNombreAndTemporada(nombre, temporada)) {
            throw ligaDuplicada(nombre, temporada);
        }

        Liga liga = ligaMapper.toEntity(request);
        liga.setNombre(nombre);
        liga.setTemporada(temporada);
        return ligaMapper.toResponse(ligaRepository.save(liga));
    }

    @Override
    @Transactional
    public LigaResponse actualizar(Long id, LigaRequest request) {
        Liga liga = buscar(id);
        String nombre = TextoCatalogo.normalizarObligatorio(request.nombre(), "nombre");
        String temporada = TextoCatalogo.normalizarObligatorio(request.temporada(), "temporada");
        validarFechas(request.fechaInicio(), request.fechaFin());
        // Se excluye la propia liga: guardarla sin cambiar nombre ni temporada no es un duplicado.
        if (ligaRepository.existsByNombreAndTemporadaAndIdNot(nombre, temporada, id)) {
            throw ligaDuplicada(nombre, temporada);
        }

        ligaMapper.updateEntity(request, liga);
        liga.setNombre(nombre);
        liga.setTemporada(temporada);
        return ligaMapper.toResponse(ligaRepository.saveAndFlush(liga));
    }

    /**
     * Al eliminar una liga se invalidan sus cachés de estadísticas (clave = id de la liga). No hace nada hasta
     * que la Fase 6 active el almacenamiento de cachés ({@code @EnableCaching}).
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#id")
    public void eliminar(Long id) {
        Liga liga = buscar(id);
        verificadoresEliminacion.verificar(Liga.class, id);
        ligaRepository.delete(liga);
        // flush: una clave foránea que el verificador no vio se convierte aquí en 409 (DataIntegrityViolation).
        ligaRepository.flush();
    }

    private Liga buscar(Long id) {
        return ligaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Liga", id));
    }

    private static void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (!fechaFin.isAfter(fechaInicio)) {
            throw new ReglaNegocioException(CODIGO_FECHAS_INVALIDAS,
                    "La fecha de fin (%s) debe ser posterior a la fecha de inicio (%s)."
                            .formatted(fechaFin, fechaInicio));
        }
    }

    private static ConflictoException ligaDuplicada(String nombre, String temporada) {
        return new ConflictoException(CODIGO_LIGA_DUPLICADA,
                "Ya existe la liga '%s' de la temporada '%s'.".formatted(nombre, temporada));
    }
}

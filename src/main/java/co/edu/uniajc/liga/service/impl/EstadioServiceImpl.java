package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.EstadioMapper;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.service.EstadioService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementación de {@link EstadioService}: reglas EST-01 y EST-02 (EST-03 vive en {@code EstadioRequest}).
 * Todo el mapeo a {@link EstadioResponse} ocurre dentro de la transacción.
 */
@Service
public class EstadioServiceImpl implements EstadioService {

    /** Código de la regla EST-01 (nombre de estadio duplicado en la ciudad). */
    static final String CODIGO_NOMBRE_DUPLICADO = "EST-01";

    /** Propiedades de {@link Estadio} por las que se acepta ordenar un listado. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "ciudad", "capacidad");

    /** Orden si el cliente no pide uno válido: por nombre y, para que la paginación sea estable, por id. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id"));

    private final EstadioRepository estadioRepository;
    private final EstadioMapper estadioMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;

    public EstadioServiceImpl(EstadioRepository estadioRepository,
                              EstadioMapper estadioMapper,
                              VerificadoresEliminacion verificadoresEliminacion) {
        this.estadioRepository = estadioRepository;
        this.estadioMapper = estadioMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<EstadioResponse> listar(Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(estadioRepository.findAll(seguro).map(estadioMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public EstadioResponse obtener(Long id) {
        return estadioMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public EstadioResponse crear(EstadioRequest request) {
        String nombre = TextoCatalogo.normalizarObligatorio(request.nombre(), "nombre");
        String ciudad = TextoCatalogo.normalizarObligatorio(request.ciudad(), "ciudad");
        if (estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCase(ciudad, nombre)) {
            throw nombreDuplicado(nombre, ciudad);
        }

        Estadio estadio = estadioMapper.toEntity(request);
        estadio.setNombre(nombre);
        estadio.setCiudad(ciudad);
        return estadioMapper.toResponse(estadioRepository.save(estadio));
    }

    @Override
    @Transactional
    public EstadioResponse actualizar(Long id, EstadioRequest request) {
        Estadio estadio = buscar(id);
        String nombre = TextoCatalogo.normalizarObligatorio(request.nombre(), "nombre");
        String ciudad = TextoCatalogo.normalizarObligatorio(request.ciudad(), "ciudad");
        // Se excluye el propio estadio: guardarlo sin cambiar su nombre no es un duplicado.
        if (estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot(ciudad, nombre, id)) {
            throw nombreDuplicado(nombre, ciudad);
        }

        estadioMapper.updateEntity(request, estadio);
        estadio.setNombre(nombre);
        estadio.setCiudad(ciudad);
        // saveAndFlush: si el índice único de la base de datos rechaza el cambio (carrera entre dos
        // solicitudes), el error aparece aquí y no al confirmar la transacción.
        return estadioMapper.toResponse(estadioRepository.saveAndFlush(estadio));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Estadio estadio = buscar(id);
        // EST-02: las fases posteriores publican verificadores que lanzan 409 si el estadio tiene equipos o partidos.
        verificadoresEliminacion.verificar(Estadio.class, id);
        estadioRepository.delete(estadio);
        // flush: una clave foránea que el verificador no vio se convierte aquí en 409 (DataIntegrityViolation).
        estadioRepository.flush();
    }

    private Estadio buscar(Long id) {
        return estadioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estadio", id));
    }

    private static ConflictoException nombreDuplicado(String nombre, String ciudad) {
        return new ConflictoException(CODIGO_NOMBRE_DUPLICADO,
                "Ya existe un estadio llamado '%s' en %s.".formatted(nombre, ciudad));
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.EntrenadorMapper;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.service.EntrenadorService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementación de {@link EntrenadorService}. Normaliza los textos de entrada y, antes de borrar, consulta a
 * los {@link co.edu.uniajc.liga.service.VerificadorEliminacion} (la fase de equipos publica el de los
 * entrenadores).
 */
@Service
public class EntrenadorServiceImpl implements EntrenadorService {

    /** Propiedades de {@link Entrenador} por las que se acepta ordenar un listado. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "nacionalidad");

    /** Orden si el cliente no pide uno válido: por nombre y, para que la paginación sea estable, por id. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id"));

    private final EntrenadorRepository entrenadorRepository;
    private final EntrenadorMapper entrenadorMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;

    public EntrenadorServiceImpl(EntrenadorRepository entrenadorRepository,
                                 EntrenadorMapper entrenadorMapper,
                                 VerificadoresEliminacion verificadoresEliminacion) {
        this.entrenadorRepository = entrenadorRepository;
        this.entrenadorMapper = entrenadorMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<EntrenadorResponse> listar(Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(entrenadorRepository.findAll(seguro).map(entrenadorMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public EntrenadorResponse obtener(Long id) {
        return entrenadorMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public EntrenadorResponse crear(EntrenadorRequest request) {
        Entrenador entrenador = entrenadorMapper.toEntity(request);
        normalizar(entrenador);
        return entrenadorMapper.toResponse(entrenadorRepository.save(entrenador));
    }

    @Override
    @Transactional
    public EntrenadorResponse actualizar(Long id, EntrenadorRequest request) {
        Entrenador entrenador = buscar(id);
        entrenadorMapper.updateEntity(request, entrenador);
        normalizar(entrenador);
        return entrenadorMapper.toResponse(entrenadorRepository.saveAndFlush(entrenador));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Entrenador entrenador = buscar(id);
        verificadoresEliminacion.verificar(Entrenador.class, id);
        entrenadorRepository.delete(entrenador);
        // flush: una clave foránea que el verificador no vio se convierte aquí en 409 (DataIntegrityViolation).
        entrenadorRepository.flush();
    }

    private Entrenador buscar(Long id) {
        return entrenadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Entrenador", id));
    }

    private static void normalizar(Entrenador entrenador) {
        entrenador.setNombre(TextoCatalogo.normalizarObligatorio(entrenador.getNombre(), "nombre"));
        entrenador.setNacionalidad(TextoCatalogo.normalizarOpcional(entrenador.getNacionalidad()));
    }
}

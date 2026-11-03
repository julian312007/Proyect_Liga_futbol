package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.ArbitroMapper;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.service.ArbitroService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementación de {@link ArbitroService}. Normaliza los textos de entrada y, antes de borrar, consulta a los
 * {@link co.edu.uniajc.liga.service.VerificadorEliminacion} (la fase de partidos publica el de los árbitros).
 */
@Service
public class ArbitroServiceImpl implements ArbitroService {

    /** Propiedades de {@link Arbitro} por las que se acepta ordenar un listado. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "nacionalidad", "aniosExperiencia");

    /** Orden si el cliente no pide uno válido: por nombre y, para que la paginación sea estable, por id. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id"));

    private final ArbitroRepository arbitroRepository;
    private final ArbitroMapper arbitroMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;

    public ArbitroServiceImpl(ArbitroRepository arbitroRepository,
                              ArbitroMapper arbitroMapper,
                              VerificadoresEliminacion verificadoresEliminacion) {
        this.arbitroRepository = arbitroRepository;
        this.arbitroMapper = arbitroMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ArbitroResponse> listar(Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(arbitroRepository.findAll(seguro).map(arbitroMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ArbitroResponse obtener(Long id) {
        return arbitroMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public ArbitroResponse crear(ArbitroRequest request) {
        Arbitro arbitro = arbitroMapper.toEntity(request);
        normalizar(arbitro);
        return arbitroMapper.toResponse(arbitroRepository.save(arbitro));
    }

    @Override
    @Transactional
    public ArbitroResponse actualizar(Long id, ArbitroRequest request) {
        Arbitro arbitro = buscar(id);
        arbitroMapper.updateEntity(request, arbitro);
        normalizar(arbitro);
        return arbitroMapper.toResponse(arbitroRepository.saveAndFlush(arbitro));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Arbitro arbitro = buscar(id);
        verificadoresEliminacion.verificar(Arbitro.class, id);
        arbitroRepository.delete(arbitro);
        // flush: una clave foránea que el verificador no vio se convierte aquí en 409 (DataIntegrityViolation).
        arbitroRepository.flush();
    }

    private Arbitro buscar(Long id) {
        return arbitroRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Arbitro", id));
    }

    private static void normalizar(Arbitro arbitro) {
        arbitro.setNombre(TextoCatalogo.normalizarObligatorio(arbitro.getNombre(), "nombre"));
        arbitro.setNacionalidad(TextoCatalogo.normalizarOpcional(arbitro.getNacionalidad()));
    }
}

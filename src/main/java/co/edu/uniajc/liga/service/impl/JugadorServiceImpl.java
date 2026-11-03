package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.JugadorMapper;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.service.JugadorService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/**
 * Implementación de {@link JugadorService}. Reglas:
 * <ul>
 *   <li><b>RN-02</b>: un jugador pertenece a un solo equipo. Al cambiar {@code equipoId} en una actualización
 *       se modifica la misma fila (el jugador se mueve, no se duplica) y el dorsal debe estar libre en el
 *       equipo destino (409, {@code DORSAL_DUPLICADO}).</li>
 *   <li>Único (equipo, dorsal) (409, {@code DORSAL_DUPLICADO}); al actualizar se excluye al propio jugador.</li>
 *   <li>{@code fechaNacimiento} anterior a hoy (422, {@code FECHA_NACIMIENTO_FUTURA}).</li>
 *   <li>Borrado: lo pueden vetar los {@code VerificadorEliminacion} de otras fases (goles o tarjetas).</li>
 * </ul>
 *
 * <p>El mapeo a {@code JugadorResponse} ocurre dentro de la transacción: {@code open-in-view} está desactivado
 * y el equipo es {@code LAZY}.
 */
@Service
public class JugadorServiceImpl implements JugadorService {

    /** Propiedades de {@code Jugador} por las que el cliente puede ordenar. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "dorsal", "posicion", "fechaNacimiento");
    static final Sort ORDEN_POR_DEFECTO = Sort.by("nombre", "id");
    static final Sort ORDEN_PLANTEL = Sort.by("dorsal", "id");

    private final JugadorRepository jugadorRepository;
    private final EquipoRepository equipoRepository;
    private final JugadorMapper jugadorMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;
    private final Clock reloj;

    @Autowired
    public JugadorServiceImpl(JugadorRepository jugadorRepository,
                              EquipoRepository equipoRepository,
                              JugadorMapper jugadorMapper,
                              VerificadoresEliminacion verificadoresEliminacion) {
        this(jugadorRepository, equipoRepository, jugadorMapper, verificadoresEliminacion, Clock.systemDefaultZone());
    }

    /** Para las pruebas: permite fijar el reloj y así no depender de la fecha en que se ejecutan. */
    JugadorServiceImpl(JugadorRepository jugadorRepository,
                       EquipoRepository equipoRepository,
                       JugadorMapper jugadorMapper,
                       VerificadoresEliminacion verificadoresEliminacion,
                       Clock reloj) {
        this.jugadorRepository = jugadorRepository;
        this.equipoRepository = equipoRepository;
        this.jugadorMapper = jugadorMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------- Lecturas

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<JugadorResponse> listar(Long equipoId, Pageable pageable) {
        if (equipoId != null) {
            return plantel(equipoId, pageable);
        }
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(jugadorRepository.findAll(seguro).map(jugadorMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<JugadorResponse> plantel(Long equipoId, Pageable pageable) {
        if (!equipoRepository.existsById(equipoId)) {
            throw new RecursoNoEncontradoException("Equipo", equipoId);
        }
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_PLANTEL);
        return PaginaResponse.de(jugadorRepository.findByEquipoId(equipoId, seguro).map(jugadorMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public JugadorResponse obtener(Long id) {
        return jugadorMapper.toResponse(buscar(id));
    }

    // ------------------------------------------------------------ Escrituras

    @Override
    @Transactional
    public JugadorResponse crear(JugadorRequest request) {
        String nombre = normalizar(request.nombre());
        validarFechaNacimiento(request.fechaNacimiento());
        Equipo equipo = buscarEquipo(request.equipoId());

        if (jugadorRepository.existsByEquipoIdAndDorsal(request.equipoId(), request.dorsal())) {
            throw dorsalDuplicado(request.dorsal());
        }

        Jugador jugador = jugadorMapper.toEntity(request);
        jugador.setNombre(nombre);
        jugador.setEquipo(equipo);
        return jugadorMapper.toResponse(jugadorRepository.save(jugador));
    }

    @Override
    @Transactional
    public JugadorResponse actualizar(Long id, JugadorRequest request) {
        Jugador jugador = buscar(id);
        String nombre = normalizar(request.nombre());
        validarFechaNacimiento(request.fechaNacimiento());
        Equipo equipo = buscarEquipo(request.equipoId());

        // RN-02: el dorsal debe estar libre en el equipo destino (que puede ser el mismo). Se excluye al propio
        // jugador para que conservar su dorsal en su equipo actual no sea un conflicto.
        if (jugadorRepository.existsByEquipoIdAndDorsalAndIdNot(request.equipoId(), request.dorsal(), id)) {
            throw dorsalDuplicado(request.dorsal());
        }

        jugadorMapper.updateEntity(request, jugador);
        jugador.setNombre(nombre);
        // RN-02: se reasigna la relación de la misma fila; nunca se crea un segundo jugador.
        jugador.setEquipo(equipo);
        // La entidad está gestionada: JPA guarda los cambios al terminar la transacción.
        return jugadorMapper.toResponse(jugador);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!jugadorRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Jugador", id);
        }
        // Punto de extensión: la Fase 5 veta el borrado de un jugador con goles o tarjetas.
        verificadoresEliminacion.verificar(Jugador.class, id);
        jugadorRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- Apoyo

    private Jugador buscar(Long id) {
        return jugadorRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Jugador", id));
    }

    private Equipo buscarEquipo(Long id) {
        return equipoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Equipo", id));
    }

    private void validarFechaNacimiento(LocalDate fechaNacimiento) {
        LocalDate hoy = LocalDate.now(reloj);
        if (!fechaNacimiento.isBefore(hoy)) {
            throw new ReglaNegocioException("FECHA_NACIMIENTO_FUTURA",
                    "La fecha de nacimiento (%s) debe ser anterior a hoy (%s).".formatted(fechaNacimiento, hoy));
        }
    }

    private static ConflictoException dorsalDuplicado(Integer dorsal) {
        return new ConflictoException("DORSAL_DUPLICADO",
                "El dorsal %d ya está ocupado por otro jugador del equipo.".formatted(dorsal));
    }

    /** Quita los espacios de los extremos y colapsa los espacios repetidos del interior. */
    private static String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ");
    }
}

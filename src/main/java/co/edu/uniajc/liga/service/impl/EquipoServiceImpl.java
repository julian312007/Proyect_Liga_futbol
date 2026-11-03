package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.EquipoMapper;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.EquipoService;
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
 * Implementación de {@link EquipoService}. Reglas:
 * <ul>
 *   <li><b>RN-03</b>: el entrenador no puede dirigir a otro equipo (409, código {@code RN-03}); al actualizar se
 *       excluye el propio equipo. La clave foránea única de la base de datos es la última defensa.</li>
 *   <li>Único (liga, nombre) sin distinguir mayúsculas (409, {@code EQUIPO_DUPLICADO}).</li>
 *   <li>{@code anioFundacion} no futuro (422, {@code ANIO_FUNDACION_FUTURO}).</li>
 *   <li>Borrado: 409 si tiene jugadores o si lo vetan los {@code VerificadorEliminacion} de otras fases.</li>
 * </ul>
 *
 * <p>El mapeo a {@code EquipoResponse} ocurre dentro de la transacción: {@code open-in-view} está desactivado y
 * las relaciones son {@code LAZY}.
 */
@Service
public class EquipoServiceImpl implements EquipoService {

    /** Propiedades de {@code Equipo} por las que el cliente puede ordenar. */
    static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "nombre", "ciudad", "anioFundacion");
    static final Sort ORDEN_POR_DEFECTO = Sort.by("nombre", "id");

    private final EquipoRepository equipoRepository;
    private final LigaRepository ligaRepository;
    private final EstadioRepository estadioRepository;
    private final EntrenadorRepository entrenadorRepository;
    private final JugadorRepository jugadorRepository;
    private final JugadorService jugadorService;
    private final EquipoMapper equipoMapper;
    private final VerificadoresEliminacion verificadoresEliminacion;
    private final Clock reloj;

    @Autowired
    public EquipoServiceImpl(EquipoRepository equipoRepository,
                             LigaRepository ligaRepository,
                             EstadioRepository estadioRepository,
                             EntrenadorRepository entrenadorRepository,
                             JugadorRepository jugadorRepository,
                             JugadorService jugadorService,
                             EquipoMapper equipoMapper,
                             VerificadoresEliminacion verificadoresEliminacion) {
        this(equipoRepository, ligaRepository, estadioRepository, entrenadorRepository, jugadorRepository,
                jugadorService, equipoMapper, verificadoresEliminacion, Clock.systemDefaultZone());
    }

    /** Para las pruebas: permite fijar el reloj y así no depender del año en que se ejecutan. */
    EquipoServiceImpl(EquipoRepository equipoRepository,
                      LigaRepository ligaRepository,
                      EstadioRepository estadioRepository,
                      EntrenadorRepository entrenadorRepository,
                      JugadorRepository jugadorRepository,
                      JugadorService jugadorService,
                      EquipoMapper equipoMapper,
                      VerificadoresEliminacion verificadoresEliminacion,
                      Clock reloj) {
        this.equipoRepository = equipoRepository;
        this.ligaRepository = ligaRepository;
        this.estadioRepository = estadioRepository;
        this.entrenadorRepository = entrenadorRepository;
        this.jugadorRepository = jugadorRepository;
        this.jugadorService = jugadorService;
        this.equipoMapper = equipoMapper;
        this.verificadoresEliminacion = verificadoresEliminacion;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------- Lecturas

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<EquipoResponse> listar(Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(equipoRepository.findAll(seguro).map(equipoMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public EquipoResponse obtener(Long id) {
        return equipoMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<JugadorResponse> plantel(Long equipoId, Pageable pageable) {
        // JugadorService comprueba que el equipo exista (404) y aplica la paginación segura.
        return jugadorService.plantel(equipoId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public CuerpoTecnicoResponse cuerpoTecnico(Long equipoId) {
        return equipoMapper.toCuerpoTecnico(buscar(equipoId));
    }

    // ------------------------------------------------------------ Escrituras

    @Override
    @Transactional
    public EquipoResponse crear(EquipoRequest request) {
        String nombre = normalizar(request.nombre());
        String ciudad = normalizar(request.ciudad());
        validarAnioFundacion(request.anioFundacion());

        Liga liga = buscarLiga(request.ligaId());
        Estadio estadio = buscarEstadio(request.estadioLocalId());
        Entrenador entrenador = buscarEntrenador(request.entrenadorId());

        if (equipoRepository.existsByLigaIdAndNombreIgnoreCase(request.ligaId(), nombre)) {
            throw nombreDuplicado(nombre);
        }
        if (equipoRepository.existsByEntrenadorId(request.entrenadorId())) {
            throw entrenadorOcupado();
        }

        Equipo equipo = equipoMapper.toEntity(request);
        equipo.setNombre(nombre);
        equipo.setCiudad(ciudad);
        equipo.setLiga(liga);
        equipo.setEstadioLocal(estadio);
        equipo.setEntrenador(entrenador);
        return equipoMapper.toResponse(equipoRepository.save(equipo));
    }

    @Override
    @Transactional
    public EquipoResponse actualizar(Long id, EquipoRequest request) {
        Equipo equipo = buscar(id);
        String nombre = normalizar(request.nombre());
        String ciudad = normalizar(request.ciudad());
        validarAnioFundacion(request.anioFundacion());

        Liga liga = buscarLiga(request.ligaId());
        Estadio estadio = buscarEstadio(request.estadioLocalId());
        Entrenador entrenador = buscarEntrenador(request.entrenadorId());

        // Se excluye el propio equipo: conservar su nombre o su entrenador no es un conflicto.
        if (equipoRepository.existsByLigaIdAndNombreIgnoreCaseAndIdNot(request.ligaId(), nombre, id)) {
            throw nombreDuplicado(nombre);
        }
        if (equipoRepository.existsByEntrenadorIdAndIdNot(request.entrenadorId(), id)) {
            throw entrenadorOcupado();
        }

        equipoMapper.updateEntity(request, equipo);
        equipo.setNombre(nombre);
        equipo.setCiudad(ciudad);
        equipo.setLiga(liga);
        equipo.setEstadioLocal(estadio);
        equipo.setEntrenador(entrenador);
        // La entidad está gestionada: JPA guarda los cambios al terminar la transacción.
        return equipoMapper.toResponse(equipo);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!equipoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Equipo", id);
        }
        if (jugadorRepository.existsByEquipoId(id)) {
            throw new ConflictoException("CONFLICTO_DEPENDENCIAS",
                    "No se puede eliminar el equipo: tiene jugadores en su plantel. "
                            + "Elimine o mueva primero a sus jugadores.");
        }
        // Punto de extensión: las fases posteriores vetan el borrado (por ejemplo, un equipo con partidos).
        verificadoresEliminacion.verificar(Equipo.class, id);
        equipoRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- Apoyo

    private Equipo buscar(Long id) {
        return equipoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Equipo", id));
    }

    private Liga buscarLiga(Long id) {
        return ligaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Liga", id));
    }

    private Estadio buscarEstadio(Long id) {
        return estadioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estadio", id));
    }

    private Entrenador buscarEntrenador(Long id) {
        return entrenadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Entrenador", id));
    }

    private void validarAnioFundacion(Integer anioFundacion) {
        int anioActual = LocalDate.now(reloj).getYear();
        if (anioFundacion > anioActual) {
            throw new ReglaNegocioException("ANIO_FUNDACION_FUTURO",
                    "El año de fundación (%d) no puede ser posterior al año actual (%d)."
                            .formatted(anioFundacion, anioActual));
        }
    }

    private static ConflictoException nombreDuplicado(String nombre) {
        return new ConflictoException("EQUIPO_DUPLICADO",
                "Ya existe un equipo llamado '%s' en esta liga.".formatted(nombre));
    }

    private static ConflictoException entrenadorOcupado() {
        return new ConflictoException("RN-03",
                "El entrenador ya dirige a otro equipo: un entrenador solo puede dirigir a un equipo.");
    }

    /** Quita los espacios de los extremos y colapsa los espacios repetidos del interior. */
    private static String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ");
    }
}

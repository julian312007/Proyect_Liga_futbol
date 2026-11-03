package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.PartidoMapper;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.repository.PartidoEspecificaciones;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.PartidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Implementación de {@link PartidoService}.
 *
 * <p>Orden de las comprobaciones al programar o reprogramar: el estado del partido (solo al editar), RN-04
 * (sin tocar la base de datos), existencia de la liga, los equipos, el estadio y el árbitro (404), RN-05,
 * RN-06 y RN-07. Se valida <em>antes</em> de modificar la entidad: una entidad gestionada ya modificada se
 * volcaría a la base de datos (flush automático) al ejecutar las consultas de choques.
 *
 * <p>Los índices únicos {@code (id_estadio, fecha_hora)} y {@code (id_arbitro, fecha_hora)} son la última
 * defensa de RN-06 y RN-07 ante dos solicitudes simultáneas; si saltan, se responde con el mismo código de la
 * regla en lugar de un 409 genérico.
 */
@Service
@Transactional
public class PartidoServiceImpl implements PartidoService {

    /**
     * Ventana de solape de RN-06 y RN-07. {@link Duration#ZERO} es la lectura literal del documento: dos
     * partidos chocan solo si tienen exactamente la misma fecha y hora. Para el criterio "más realista" del
     * PROMPT (un partido dura unas 2 horas), basta cambiar esta constante a {@code Duration.ofHours(2)}: no hay
     * ningún otro lugar donde se compare el horario (ver {@link #chocaEnHorario}).
     */
    static final Duration VENTANA_SOLAPE = Duration.ZERO;

    /** Propiedades por las que el calendario acepta ordenar. */
    static final Set<String> ORDEN_PERMITIDO = Set.of("id", "jornada", "fechaHora", "estado");

    /** Calendario: por jornada y, dentro de ella, cronológico. */
    static final Sort ORDEN_POR_DEFECTO = Sort.by("jornada", "fechaHora", "id");

    private final PartidoRepository partidoRepository;
    private final PartidoMapper partidoMapper;
    private final LigaRepository ligaRepository;
    private final EquipoRepository equipoRepository;
    private final EstadioRepository estadioRepository;
    private final ArbitroRepository arbitroRepository;
    private final Duration ventanaSolape;

    @Autowired
    public PartidoServiceImpl(PartidoRepository partidoRepository,
                              PartidoMapper partidoMapper,
                              LigaRepository ligaRepository,
                              EquipoRepository equipoRepository,
                              EstadioRepository estadioRepository,
                              ArbitroRepository arbitroRepository) {
        this(partidoRepository, partidoMapper, ligaRepository, equipoRepository, estadioRepository,
                arbitroRepository, VENTANA_SOLAPE);
    }

    /** Solo para pruebas: permite demostrar que cambiar la ventana cambia el resultado de RN-06 y RN-07. */
    PartidoServiceImpl(PartidoRepository partidoRepository,
                       PartidoMapper partidoMapper,
                       LigaRepository ligaRepository,
                       EquipoRepository equipoRepository,
                       EstadioRepository estadioRepository,
                       ArbitroRepository arbitroRepository,
                       Duration ventanaSolape) {
        this.partidoRepository = partidoRepository;
        this.partidoMapper = partidoMapper;
        this.ligaRepository = ligaRepository;
        this.equipoRepository = equipoRepository;
        this.estadioRepository = estadioRepository;
        this.arbitroRepository = arbitroRepository;
        this.ventanaSolape = ventanaSolape;
    }

    // ------------------------------------------------------------------------------ Escritura

    @Override
    public PartidoResponse crear(PartidoRequest request) {
        Relaciones relaciones = validar(request, PartidoRepository.SIN_EXCLUSION);
        Partido partido = partidoMapper.toEntity(request);
        asignar(partido, relaciones);
        return guardar(partido);
    }

    @Override
    public PartidoResponse actualizar(Long id, PartidoRequest request) {
        Partido partido = buscar(id);
        verificarModificable(partido);
        // Al editar se excluye el propio partido de los choques (RN-05 a RN-07).
        Relaciones relaciones = validar(request, id);
        partidoMapper.updateEntity(request, partido);
        asignar(partido, relaciones);
        return guardar(partido);
    }

    @Override
    public void eliminar(Long id) {
        Partido partido = buscar(id);
        verificarModificable(partido);
        partidoRepository.delete(partido);
    }

    @Override
    public PartidoResponse iniciar(Long id) {
        Partido partido = buscar(id);
        if (partido.getEstado() != EstadoPartido.PROGRAMADO) {
            throw new ConflictoException("PARTIDO_ESTADO_INVALIDO",
                    "Solo un partido PROGRAMADO puede iniciarse; este partido está %s.".formatted(partido.getEstado()));
        }
        partido.setEstado(EstadoPartido.EN_JUEGO);
        return partidoMapper.toResponse(partidoRepository.save(partido));
    }

    // -------------------------------------------------------------------------------- Lectura

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PartidoResponse> listar(Long ligaId, Integer jornada, Long equipoId, Long estadioId,
                                                  Pageable pageable) {
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDEN_PERMITIDO, ORDEN_POR_DEFECTO);
        Page<Partido> pagina = partidoRepository.findAll(
                PartidoEspecificaciones.calendario(ligaId, jornada, equipoId, estadioId), seguro);
        // El mapeo ocurre dentro de la transacción: las relaciones son LAZY.
        return PaginaResponse.de(pagina.map(partidoMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PartidoResponse obtener(Long id) {
        return partidoMapper.toResponse(buscar(id));
    }

    // ---------------------------------------------------------------------------- Validaciones

    /**
     * Aplica RN-04 a RN-07 y devuelve las relaciones ya resueltas.
     *
     * @param excluirId partido que se está editando (no choca consigo mismo), o {@link PartidoRepository#SIN_EXCLUSION}
     */
    private Relaciones validar(PartidoRequest request, long excluirId) {
        // RN-04: se comprueba primero porque no necesita la base de datos.
        if (Objects.equals(request.equipoLocalId(), request.equipoVisitanteId())) {
            throw new ReglaNegocioException("RN-04", "Un equipo no puede enfrentarse a sí mismo.");
        }

        Liga liga = ligaRepository.findById(request.ligaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Liga", request.ligaId()));
        Equipo local = equipoRepository.findById(request.equipoLocalId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo", request.equipoLocalId()));
        Equipo visitante = equipoRepository.findById(request.equipoVisitanteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo", request.equipoVisitanteId()));
        Estadio estadio = estadioRepository.findById(request.estadioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estadio", request.estadioId()));
        Arbitro arbitro = arbitroRepository.findById(request.arbitroId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Arbitro", request.arbitroId()));

        // RN-05: un equipo no juega dos partidos en la misma jornada de la misma liga (local o visitante).
        verificarJornadaLibre(request, local, request.equipoLocalId(), excluirId);
        verificarJornadaLibre(request, visitante, request.equipoVisitanteId(), excluirId);

        // RN-06: el estadio no puede tener dos partidos a la misma fecha y hora.
        if (chocaEnHorario(request.fechaHora(), (desde, hasta) ->
                partidoRepository.horariosOcupadosDelEstadio(request.estadioId(), excluirId, desde, hasta))) {
            throw new ConflictoException("RN-06", "El estadio '%s' ya tiene un partido a esa fecha y hora."
                    .formatted(estadio.getNombre()));
        }

        // RN-07: el árbitro no puede dirigir dos partidos a la misma fecha y hora.
        if (chocaEnHorario(request.fechaHora(), (desde, hasta) ->
                partidoRepository.horariosOcupadosDelArbitro(request.arbitroId(), excluirId, desde, hasta))) {
            throw new ConflictoException("RN-07", "El árbitro '%s' ya dirige un partido a esa fecha y hora."
                    .formatted(arbitro.getNombre()));
        }

        return new Relaciones(liga, local, visitante, estadio, arbitro);
    }

    private void verificarJornadaLibre(PartidoRequest request, Equipo equipo, Long equipoId, long excluirId) {
        long partidos = partidoRepository.contarPartidosDelEquipoEnJornada(
                request.ligaId(), request.jornada(), equipoId, excluirId);
        if (partidos > 0) {
            throw new ConflictoException("RN-05", "El equipo '%s' ya tiene un partido en la jornada %d de esta liga."
                    .formatted(equipo.getNombre(), request.jornada()));
        }
    }

    /**
     * ÚNICO lugar donde se decide si un horario choca con otros (RN-06 y RN-07). Dos horarios chocan si son
     * exactamente el mismo instante o si distan menos que {@link #ventanaSolape}. Con la ventana en
     * {@link Duration#ZERO} solo chocan los instantes idénticos; con 2 horas, un partido a las 20:00 choca con
     * otro a las 21:59 pero no con uno a las 22:00 (el primero ya terminó).
     *
     * <p>La consulta recibe el rango {@code [propuesta - ventana, propuesta + ventana]} solo para no traer todos
     * los partidos del estadio o del árbitro; la decisión final se toma aquí, comparando cada candidato.
     *
     * @param ocupados consulta de los horarios ocupados dentro de un rango (desde y hasta incluidos)
     */
    private boolean chocaEnHorario(Instant propuesta, BiFunction<Instant, Instant, List<Instant>> ocupados) {
        List<Instant> candidatos = ocupados.apply(propuesta.minus(ventanaSolape), propuesta.plus(ventanaSolape));
        return candidatos.stream().anyMatch(existente -> {
            Duration distancia = Duration.between(existente, propuesta).abs();
            return distancia.isZero() || distancia.compareTo(ventanaSolape) < 0;
        });
    }

    /** RN-12 (parte del partido): un partido finalizado, o en juego, no se modifica ni se elimina. */
    private static void verificarModificable(Partido partido) {
        switch (partido.getEstado()) {
            case FINALIZADO -> throw new ConflictoException("RN-12",
                    "Un partido finalizado no puede modificarse ni eliminarse.");
            case EN_JUEGO -> throw new ConflictoException("PARTIDO_EN_JUEGO",
                    "Un partido en juego no puede modificarse ni eliminarse.");
            case PROGRAMADO -> {
                // Es el único estado editable.
            }
        }
    }

    // ----------------------------------------------------------------------------------- Apoyo

    private Partido buscar(Long id) {
        return partidoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Partido", id));
    }

    private static void asignar(Partido partido, Relaciones relaciones) {
        partido.setLiga(relaciones.liga());
        partido.setEquipoLocal(relaciones.local());
        partido.setEquipoVisitante(relaciones.visitante());
        partido.setEstadio(relaciones.estadio());
        partido.setArbitro(relaciones.arbitro());
    }

    /** Guarda y vuelca a la base de datos para que un índice único violado se detecte aquí y no al confirmar. */
    private PartidoResponse guardar(Partido partido) {
        try {
            return partidoMapper.toResponse(partidoRepository.saveAndFlush(partido));
        } catch (DataIntegrityViolationException ex) {
            throw traducir(ex);
        }
    }

    /** Última defensa de RN-06 y RN-07: reconoce el índice único violado por su nombre (V9__partido.sql). */
    static RuntimeException traducir(DataIntegrityViolationException ex) {
        String detalle = ex.getMessage() + " " + ex.getMostSpecificCause().getMessage();
        if (detalle.contains("uq_partido_estadio_fecha")) {
            return new ConflictoException("RN-06", "El estadio ya tiene un partido a esa fecha y hora.");
        }
        if (detalle.contains("uq_partido_arbitro_fecha")) {
            return new ConflictoException("RN-07", "El árbitro ya dirige un partido a esa fecha y hora.");
        }
        return ex;
    }

    /** Entidades que el partido referencia, resueltas por id. */
    private record Relaciones(Liga liga, Equipo local, Equipo visitante, Estadio estadio, Arbitro arbitro) {
    }
}

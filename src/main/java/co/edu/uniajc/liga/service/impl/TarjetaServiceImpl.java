package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.TarjetaMapper;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.service.PaginacionSegura;
import co.edu.uniajc.liga.service.TarjetaService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementación de {@link TarjetaService}. Reglas: estado del partido (RN-12), pertenencia (RN-08) y
 * suspensión (RN-10) vía {@link EventoPartidoValidator}; y aquí, la doble amarilla (RN-09) y el rechazo de
 * tarjetas a un jugador ya expulsado en el partido ({@code TARJETA_JUGADOR_EXPULSADO}).
 *
 * <p>RN-09: la roja automática se guarda en el mismo método {@code @Transactional} que la segunda
 * amarilla: o quedan las dos o no queda ninguna.
 */
@Service
@Transactional
public class TarjetaServiceImpl implements TarjetaService {

    static final String TARJETA_JUGADOR_EXPULSADO = "TARJETA_JUGADOR_EXPULSADO";

    private static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "minuto", "tipo");
    private static final Sort ORDEN_POR_DEFECTO = Sort.by("minuto", "id");

    private final TarjetaRepository tarjetaRepository;
    private final PartidoEventosRepository partidoRepository;
    private final EventoPartidoValidator validador;
    private final TarjetaMapper tarjetaMapper;

    public TarjetaServiceImpl(TarjetaRepository tarjetaRepository,
                              PartidoEventosRepository partidoRepository,
                              EventoPartidoValidator validador,
                              TarjetaMapper tarjetaMapper) {
        this.tarjetaRepository = tarjetaRepository;
        this.partidoRepository = partidoRepository;
        this.validador = validador;
        this.tarjetaMapper = tarjetaMapper;
    }

    @Override
    public TarjetaResponse registrar(Long partidoId, TarjetaRequest request) {
        Partido partido = validador.cargarPartidoEnJuego(partidoId);
        Jugador jugador = validador.cargarJugadorHabilitado(partido, request.jugadorId());

        // Un jugador expulsado en este partido ya no puede recibir más tarjetas.
        if (tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(partidoId, jugador.getId(), TipoTarjeta.ROJA)) {
            throw new ReglaNegocioException(TARJETA_JUGADOR_EXPULSADO,
                    "El jugador %d (%s) ya fue expulsado en el partido %d: no admite más tarjetas."
                            .formatted(jugador.getId(), jugador.getNombre(), partidoId));
        }

        // RN-09: se cuentan las amarillas ANTES de guardar la nueva.
        long amarillasPrevias = request.tipo() == TipoTarjeta.AMARILLA
                ? tarjetaRepository.countByPartidoIdAndJugadorIdAndTipo(partidoId, jugador.getId(), TipoTarjeta.AMARILLA)
                : 0;

        Tarjeta tarjeta = tarjetaMapper.toEntity(request);
        tarjeta.setPartido(partido);
        tarjeta.setJugador(jugador);
        Tarjeta guardada = tarjetaRepository.save(tarjeta);

        if (request.tipo() == TipoTarjeta.AMARILLA && amarillasPrevias >= 1) {
            Tarjeta roja = new Tarjeta();
            roja.setPartido(partido);
            roja.setJugador(jugador);
            roja.setTipo(TipoTarjeta.ROJA);
            roja.setMinuto(request.minuto());
            tarjetaRepository.save(roja);
        }
        return tarjetaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<TarjetaResponse> listar(Long partidoId, Pageable pageable) {
        if (!partidoRepository.existsById(partidoId)) {
            throw new RecursoNoEncontradoException("Partido", partidoId);
        }
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(tarjetaRepository.findByPartidoId(partidoId, seguro).map(tarjetaMapper::toResponse));
    }

    @Override
    public void eliminar(Long partidoId, Long tarjetaId) {
        validador.cargarPartidoEnJuego(partidoId);
        Tarjeta tarjeta = tarjetaRepository.findByIdAndPartidoId(tarjetaId, partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarjeta", tarjetaId));
        tarjetaRepository.delete(tarjeta);
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.service.SuspensionService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Comprobaciones que comparten {@link GolServiceImpl} y {@link TarjetaServiceImpl} antes de tocar los goles
 * o las tarjetas de un partido. Es parte de la capa de servicio (no la usa ningún controlador) y siempre
 * se invoca dentro de la transacción del servicio que la llama.
 *
 * <ul>
 *   <li><b>Estado del partido</b> (RN-12): solo se cambian goles y tarjetas con el partido
 *       {@code EN_JUEGO}; un partido {@code FINALIZADO} no admite cambios ({@code RN-12}) y uno
 *       {@code PROGRAMADO} tampoco ({@code PARTIDO_NO_EN_JUEGO}). Ambos son 409.</li>
 *   <li><b>RN-08</b>: el jugador debe ser de uno de los dos equipos del partido (422).</li>
 *   <li><b>RN-10</b>: un jugador suspendido por una expulsión previa no puede tener eventos (422).</li>
 * </ul>
 */
@Component
public class EventoPartidoValidator {

    static final String RN_08 = "RN-08";
    static final String RN_10 = "RN-10";
    static final String RN_12 = "RN-12";
    static final String PARTIDO_NO_EN_JUEGO = "PARTIDO_NO_EN_JUEGO";

    private final PartidoEventosRepository partidoRepository;
    private final JugadorRepository jugadorRepository;
    private final SuspensionService suspensionService;

    public EventoPartidoValidator(PartidoEventosRepository partidoRepository,
                                  JugadorRepository jugadorRepository,
                                  SuspensionService suspensionService) {
        this.partidoRepository = partidoRepository;
        this.jugadorRepository = jugadorRepository;
        this.suspensionService = suspensionService;
    }

    /**
     * Lee el partido con bloqueo de escritura (serializa los cambios concurrentes sobre el mismo partido) y
     * exige que esté {@code EN_JUEGO}.
     *
     * @throws RecursoNoEncontradoException (404) si no existe
     * @throws ConflictoException (409) {@code RN-12} si está finalizado; {@code PARTIDO_NO_EN_JUEGO} si
     *         aún está programado
     */
    public Partido cargarPartidoEnJuego(Long partidoId) {
        Partido partido = partidoRepository.buscarBloqueadoPorId(partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido", partidoId));
        exigirEnJuego(partido);
        return partido;
    }

    /**
     * Resuelve al jugador por id y comprueba que puede tener eventos en el partido.
     *
     * @throws RecursoNoEncontradoException (404) si el jugador no existe
     * @throws ReglaNegocioException (422) {@code RN-08} si no es de ninguno de los dos equipos; {@code RN-10}
     *         si está suspendido en este partido
     */
    public Jugador cargarJugadorHabilitado(Partido partido, Long jugadorId) {
        Jugador jugador = jugadorRepository.findById(jugadorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Jugador", jugadorId));

        Long equipoId = jugador.getEquipo().getId();
        boolean esDeUnoDeLosEquipos = Objects.equals(equipoId, partido.getEquipoLocal().getId())
                || Objects.equals(equipoId, partido.getEquipoVisitante().getId());
        if (!esDeUnoDeLosEquipos) {
            throw new ReglaNegocioException(RN_08,
                    "El jugador %d (%s) no pertenece a ninguno de los dos equipos del partido %d."
                            .formatted(jugador.getId(), jugador.getNombre(), partido.getId()));
        }
        if (suspensionService.estaSuspendido(jugador.getId(), partido.getId())) {
            throw new ReglaNegocioException(RN_10,
                    ("El jugador %d (%s) está suspendido para el partido %d por una expulsión en el partido "
                            + "anterior de su equipo.").formatted(jugador.getId(), jugador.getNombre(), partido.getId()));
        }
        return jugador;
    }

    private static void exigirEnJuego(Partido partido) {
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new ConflictoException(RN_12,
                    "El partido %d está FINALIZADO: no admite cambios ni modificaciones de sus goles y tarjetas."
                            .formatted(partido.getId()));
        }
        if (partido.getEstado() != EstadoPartido.EN_JUEGO) {
            throw new ConflictoException(PARTIDO_NO_EN_JUEGO,
                    "El partido %d está %s: los goles y las tarjetas solo se cambian con el partido EN_JUEGO."
                            .formatted(partido.getId(), partido.getEstado()));
        }
    }
}

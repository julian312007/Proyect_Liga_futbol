package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.service.SuspensionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementación de {@link SuspensionService} (RN-10).
 *
 * <p>Definición (la del Javadoc de la interfaz): un jugador está suspendido en el partido P si recibió una
 * ROJA (directa o por doble amarilla) en el partido inmediatamente anterior de su equipo en la misma liga.
 * El "anterior" es el último partido del equipo con {@code fechaHora} estrictamente menor que la de P; no
 * importa su estado ni su jornada. "Su equipo" es el equipo actual del jugador.
 *
 * <p>Es de solo lectura, pero se invoca desde las transacciones de escritura de goles y tarjetas: en ese
 * caso participa de ellas.
 */
@Service
@Transactional(readOnly = true)
public class SuspensionServiceImpl implements SuspensionService {

    private final PartidoEventosRepository partidoRepository;
    private final JugadorRepository jugadorRepository;
    private final TarjetaRepository tarjetaRepository;

    public SuspensionServiceImpl(PartidoEventosRepository partidoRepository,
                                 JugadorRepository jugadorRepository,
                                 TarjetaRepository tarjetaRepository) {
        this.partidoRepository = partidoRepository;
        this.jugadorRepository = jugadorRepository;
        this.tarjetaRepository = tarjetaRepository;
    }

    @Override
    public boolean estaSuspendido(Long jugadorId, Long partidoId) {
        Partido partido = buscarPartido(partidoId);
        Jugador jugador = jugadorRepository.findById(jugadorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Jugador", jugadorId));

        Long equipoId = jugador.getEquipo().getId();
        boolean juegaEnElPartido = Objects.equals(equipoId, partido.getEquipoLocal().getId())
                || Objects.equals(equipoId, partido.getEquipoVisitante().getId());
        if (!juegaEnElPartido) {
            return false; // Si su equipo no juega este partido, no hay nada que cumplir (RN-08 lo rechaza aparte).
        }
        return ultimoPartidoAnterior(partido, equipoId)
                .map(anterior -> tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(
                        anterior.getId(), jugadorId, TipoTarjeta.ROJA))
                .orElse(false);
    }

    @Override
    public List<SuspendidoResponse> suspendidosEnPartido(Long partidoId) {
        Partido partido = buscarPartido(partidoId);
        List<SuspendidoResponse> suspendidos = new ArrayList<>();
        for (Equipo equipo : List.of(partido.getEquipoLocal(), partido.getEquipoVisitante())) {
            ultimoPartidoAnterior(partido, equipo.getId()).ifPresent(anterior ->
                    tarjetaRepository.findDeEquipoEnPartido(anterior.getId(), equipo.getId(), TipoTarjeta.ROJA)
                            .forEach(roja -> suspendidos.add(new SuspendidoResponse(
                                    roja.getJugador().getId(), roja.getJugador().getNombre(),
                                    equipo.getId(), equipo.getNombre(),
                                    anterior.getId(), anterior.getJornada()))));
        }
        return suspendidos.stream().distinct().toList();
    }

    private Partido buscarPartido(Long partidoId) {
        return partidoRepository.findById(partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido", partidoId));
    }

    /** Último partido del equipo en la liga de {@code partido}, anterior (por fecha y hora) a {@code partido}. */
    private Optional<Partido> ultimoPartidoAnterior(Partido partido, Long equipoId) {
        return partidoRepository.buscarAnterioresDeEquipo(
                        partido.getLiga().getId(), equipoId, partido.getFechaHora(), PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }
}

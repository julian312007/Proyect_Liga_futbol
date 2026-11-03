package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.SancionJugadorResponse;
import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.EstadisticasTarjetaRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.SancionService;
import co.edu.uniajc.liga.service.SuspensionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sanciones de una liga (F7). Las tarjetas se cuentan en el servicio con <i>streams</i>; la lista de
 * suspendidos no se recalcula aquí: delega en {@link SuspensionService}, que es la definición única de RN-10
 * (la implementa la Fase 5).
 *
 * <p>Estas consultas no se guardan en caché: cambian con cada tarjeta, no solo al finalizar un partido.
 */
@Service
public class SancionServiceImpl implements SancionService {

    private static final Comparator<SancionJugadorResponse> ORDEN_SANCIONES = Comparator
            .comparingInt((SancionJugadorResponse s) -> s.rojas()).reversed()
            .thenComparing(Comparator.comparingInt((SancionJugadorResponse s) -> s.amarillas()).reversed())
            .thenComparing((SancionJugadorResponse s) -> s.jugador(), OrdenAlfabetico.POR_NOMBRE)
            .thenComparing((SancionJugadorResponse s) -> s.jugadorId());

    private static final Comparator<SuspendidoResponse> ORDEN_SUSPENDIDOS = Comparator
            .comparing((SuspendidoResponse s) -> s.equipo(), OrdenAlfabetico.POR_NOMBRE)
            .thenComparing((SuspendidoResponse s) -> s.jugador(), OrdenAlfabetico.POR_NOMBRE)
            .thenComparing((SuspendidoResponse s) -> s.jugadorId());

    private final LigaRepository ligaRepository;
    private final EstadisticasTarjetaRepository tarjetaRepository;
    private final EstadisticasPartidoRepository partidoRepository;
    private final SuspensionService suspensionService;

    public SancionServiceImpl(LigaRepository ligaRepository,
                              EstadisticasTarjetaRepository tarjetaRepository,
                              EstadisticasPartidoRepository partidoRepository,
                              SuspensionService suspensionService) {
        this.ligaRepository = ligaRepository;
        this.tarjetaRepository = tarjetaRepository;
        this.partidoRepository = partidoRepository;
        this.suspensionService = suspensionService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SancionJugadorResponse> listarSanciones(Long ligaId) {
        verificarLiga(ligaId);

        Map<Long, Conteo> porJugador = new LinkedHashMap<>();
        for (Tarjeta tarjeta : tarjetaRepository.buscarDeLiga(ligaId)) {
            Conteo conteo = porJugador.computeIfAbsent(tarjeta.getJugador().getId(),
                    id -> new Conteo(tarjeta.getJugador()));
            if (tarjeta.getTipo() == TipoTarjeta.ROJA) {
                conteo.rojas++;
            } else {
                conteo.amarillas++;
            }
        }
        return porJugador.values().stream()
                .map(Conteo::aRespuesta)
                .sorted(ORDEN_SANCIONES)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuspendidoResponse> listarSuspendidos(Long ligaId, Integer jornada) {
        verificarLiga(ligaId);

        return partidoRepository.buscarIdsDeJornada(ligaId, jornada).stream()
                .flatMap(partidoId -> suspensionService.suspendidosEnPartido(partidoId).stream())
                .distinct()
                .sorted(ORDEN_SUSPENDIDOS)
                .toList();
    }

    private void verificarLiga(Long ligaId) {
        if (!ligaRepository.existsById(ligaId)) {
            throw new RecursoNoEncontradoException("Liga", ligaId);
        }
    }

    /** Contadores de tarjetas de un jugador mientras se recorren las tarjetas. */
    private static final class Conteo {
        private final Jugador jugador;
        private int amarillas;
        private int rojas;

        private Conteo(Jugador jugador) {
            this.jugador = jugador;
        }

        private SancionJugadorResponse aRespuesta() {
            return new SancionJugadorResponse(jugador.getId(), jugador.getNombre(), jugador.getEquipo().getId(),
                    jugador.getEquipo().getNombre(), amarillas, rojas);
        }
    }
}

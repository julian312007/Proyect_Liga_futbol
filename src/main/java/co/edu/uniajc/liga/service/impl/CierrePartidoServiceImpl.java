package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.ResultadoPartidoMapper;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.service.CierrePartidoService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Implementación de {@link CierrePartidoService}.
 *
 * <p><b>RN-11</b> (marcador = goles registrados): {@code golesLocal} es la suma de los goles NORMAL y PENAL de
 * jugadores del equipo local más los AUTOGOL de jugadores del visitante (el autogol suma al equipo
 * contrario); {@code golesVisitante} es lo análogo. Si el cuerpo no coincide, 422 y el partido no cambia.
 *
 * <p><b>RN-12</b>: un partido {@code FINALIZADO} es terminal. Al finalizar se toma un bloqueo de escritura
 * sobre el partido, así un gol que se esté registrando a la vez espera, y luego ve el partido finalizado y
 * se rechaza.
 *
 * <p><b>Caché (D-3)</b>: {@code @CacheEvict} invalida la tabla de posiciones y los goleadores de la liga del
 * partido. Se anota la implementación (el servicio), no el controlador. No hace nada hasta que la Fase 6
 * active {@code @EnableCaching}.
 */
@Service
@Transactional
public class CierrePartidoServiceImpl implements CierrePartidoService {

    static final String RN_11 = "RN-11";
    static final String RN_12 = "RN-12";
    static final String PARTIDO_ESTADO_INVALIDO = "PARTIDO_ESTADO_INVALIDO";

    private final PartidoEventosRepository partidoRepository;
    private final GolRepository golRepository;
    private final ResultadoPartidoMapper resultadoMapper;

    public CierrePartidoServiceImpl(PartidoEventosRepository partidoRepository,
                                    GolRepository golRepository,
                                    ResultadoPartidoMapper resultadoMapper) {
        this.partidoRepository = partidoRepository;
        this.golRepository = golRepository;
        this.resultadoMapper = resultadoMapper;
    }

    @Override
    @CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#result.ligaId()")
    public ResultadoPartidoResponse finalizar(Long partidoId, FinalizarPartidoRequest request) {
        Partido partido = partidoRepository.buscarBloqueadoPorId(partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido", partidoId));

        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new ConflictoException(RN_12,
                    "El partido %d ya está FINALIZADO: no puede modificarse.".formatted(partidoId));
        }
        if (partido.getEstado() != EstadoPartido.EN_JUEGO) {
            throw new ConflictoException(PARTIDO_ESTADO_INVALIDO,
                    "El partido %d está %s: solo se puede finalizar un partido EN_JUEGO."
                            .formatted(partidoId, partido.getEstado()));
        }

        // RN-11: el marcador enviado debe ser exactamente el que producen los goles registrados.
        List<Gol> goles = golRepository.findConEquipoByPartidoId(partidoId);
        int esperadosLocal = golesDe(goles, partido.getEquipoLocal().getId(), partido.getEquipoVisitante().getId());
        int esperadosVisitante = golesDe(goles, partido.getEquipoVisitante().getId(), partido.getEquipoLocal().getId());
        if (request.golesLocal() != esperadosLocal || request.golesVisitante() != esperadosVisitante) {
            throw new ReglaNegocioException(RN_11,
                    ("El marcador %d-%d no coincide con los goles registrados del partido %d (%d-%d)."
                            + " Corrija el marcador o los goles.")
                            .formatted(request.golesLocal(), request.golesVisitante(), partidoId,
                                    esperadosLocal, esperadosVisitante));
        }

        partido.setGolesLocal(request.golesLocal());
        partido.setGolesVisitante(request.golesVisitante());
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoRepository.save(partido);
        return resultadoMapper.toResponse(partido);
    }

    /**
     * Goles que suma el equipo {@code equipoId}: NORMAL y PENAL de sus jugadores, más los AUTOGOL de los
     * jugadores del {@code rivalId}.
     */
    private static int golesDe(List<Gol> goles, Long equipoId, Long rivalId) {
        int total = 0;
        for (Gol gol : goles) {
            Long equipoDelJugador = gol.getJugador().getEquipo().getId();
            if (gol.getTipo() == TipoGol.AUTOGOL) {
                if (Objects.equals(equipoDelJugador, rivalId)) {
                    total++;
                }
            } else if (Objects.equals(equipoDelJugador, equipoId)) {
                total++;
            }
        }
        return total;
    }
}

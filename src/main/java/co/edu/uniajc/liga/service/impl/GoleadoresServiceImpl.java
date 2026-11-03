package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasGolRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.GoleadoresService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tabla de goleadores calculada al consultar, con <i>streams</i> sobre los goles de los partidos
 * {@code FINALIZADO}. Suman los goles {@code NORMAL} y {@code PENAL}; el {@code AUTOGOL} no.
 *
 * <p>Se guarda en la caché {@link NombresCache#GOLEADORES} con el id de la liga como clave (ver
 * {@link TablaPosicionesServiceImpl} para la invalidación). El equipo que se muestra es el actual del
 * jugador: si cambia de equipo (RN-02), sus goles anteriores figuran con el equipo nuevo.
 */
@Service
public class GoleadoresServiceImpl implements GoleadoresService {

    private static final Comparator<Acumulado> ORDEN = Comparator
            .comparingInt((Acumulado a) -> a.goles).reversed()
            .thenComparing((Acumulado a) -> a.jugador.getNombre(), OrdenAlfabetico.POR_NOMBRE)
            .thenComparing((Acumulado a) -> a.jugador.getId());

    private final LigaRepository ligaRepository;
    private final EstadisticasGolRepository golRepository;

    public GoleadoresServiceImpl(LigaRepository ligaRepository, EstadisticasGolRepository golRepository) {
        this.ligaRepository = ligaRepository;
        this.golRepository = golRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = NombresCache.GOLEADORES, key = "#ligaId")
    public List<GoleadorResponse> obtenerGoleadores(Long ligaId) {
        if (!ligaRepository.existsById(ligaId)) {
            throw new RecursoNoEncontradoException("Liga", ligaId);
        }

        Map<Long, Acumulado> porJugador = new LinkedHashMap<>();
        golRepository.buscarDeLigaPorEstadoDelPartido(ligaId, EstadoPartido.FINALIZADO).stream()
                .filter(gol -> gol.getTipo() != TipoGol.AUTOGOL)
                .map(Gol::getJugador)
                .forEach(jugador -> porJugador.computeIfAbsent(jugador.getId(), id -> new Acumulado(jugador)).goles++);

        List<Acumulado> ordenados = porJugador.values().stream().sorted(ORDEN).toList();
        return IntStream.range(0, ordenados.size())
                .mapToObj(i -> ordenados.get(i).aRespuesta(i + 1))
                .toList();
    }

    /** Contador de goles de un jugador mientras se recorren los goles. */
    private static final class Acumulado {
        private final Jugador jugador;
        private int goles;

        private Acumulado(Jugador jugador) {
            this.jugador = jugador;
        }

        private GoleadorResponse aRespuesta(int posicion) {
            return new GoleadorResponse(posicion, jugador.getId(), jugador.getNombre(),
                    jugador.getEquipo().getId(), jugador.getEquipo().getNombre(), goles);
        }
    }
}

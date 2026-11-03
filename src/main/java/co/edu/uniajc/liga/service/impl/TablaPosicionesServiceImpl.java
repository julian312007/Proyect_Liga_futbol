package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasEquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.TablaPosicionesService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tabla de posiciones calculada al consultar, con <i>streams</i> sobre los partidos {@code FINALIZADO}
 * (opción (b) del plan: es más fácil de leer y de probar que una agregación en JPQL; la caché amortigua el
 * costo).
 *
 * <p>Se guarda en la caché {@link NombresCache#TABLA_POSICIONES} con el id de la liga como clave. La
 * anotación está en esta clase de servicio y el controlador la invoca a través de su interfaz, de modo que la
 * llamada pasa por el proxy de Spring. Se invalida al finalizar un partido (Fase 5) y al eliminar la liga
 * (Fase 2); el TTL de 10 minutos es el respaldo si una invalidación se pierde.
 */
@Service
public class TablaPosicionesServiceImpl implements TablaPosicionesService {

    /** RN-01: puntos por victoria. */
    static final int PUNTOS_VICTORIA = 3;
    /** RN-01: puntos por empate. */
    static final int PUNTOS_EMPATE = 1;
    /** RN-01: puntos por derrota. */
    static final int PUNTOS_DERROTA = 0;

    /** Puntos, luego diferencia de gol y luego goles a favor (de mayor a menor); nombre e id fijan un orden estable. */
    private static final Comparator<Fila> ORDEN_DE_LA_TABLA = Comparator
            .comparingInt((Fila f) -> f.puntos()).reversed()
            .thenComparing(Comparator.comparingInt((Fila f) -> f.diferenciaDeGol()).reversed())
            .thenComparing(Comparator.comparingInt((Fila f) -> f.golesAFavor).reversed())
            .thenComparing((Fila f) -> f.nombre, OrdenAlfabetico.POR_NOMBRE)
            .thenComparing((Fila f) -> f.equipoId);

    private final LigaRepository ligaRepository;
    private final EstadisticasEquipoRepository equipoRepository;
    private final EstadisticasPartidoRepository partidoRepository;

    public TablaPosicionesServiceImpl(LigaRepository ligaRepository,
                                      EstadisticasEquipoRepository equipoRepository,
                                      EstadisticasPartidoRepository partidoRepository) {
        this.ligaRepository = ligaRepository;
        this.equipoRepository = equipoRepository;
        this.partidoRepository = partidoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = NombresCache.TABLA_POSICIONES, key = "#ligaId")
    public List<PosicionEquipoResponse> obtenerTabla(Long ligaId) {
        if (!ligaRepository.existsById(ligaId)) {
            throw new RecursoNoEncontradoException("Liga", ligaId);
        }

        // Todos los equipos de la liga entran con ceros, hayan jugado o no. Si un partido finalizado trae un
        // equipo que no está inscrito en la liga (el modelo no lo impide), también se cuenta.
        Map<Long, Fila> filas = new LinkedHashMap<>();
        for (Equipo equipo : equipoRepository.buscarDeLiga(ligaId)) {
            filas.computeIfAbsent(equipo.getId(), id -> new Fila(id, equipo.getNombre()));
        }

        partidoRepository.buscarDeLigaPorEstado(ligaId, EstadoPartido.FINALIZADO).stream()
                // La consulta ya filtra por estado; se repite aquí para que solo un partido terminado dé puntos.
                .filter(partido -> partido.getEstado() == EstadoPartido.FINALIZADO)
                .forEach(partido -> registrar(filas, partido));

        List<Fila> ordenadas = filas.values().stream().sorted(ORDEN_DE_LA_TABLA).toList();
        return IntStream.range(0, ordenadas.size())
                .mapToObj(i -> ordenadas.get(i).aRespuesta(i + 1))
                .toList();
    }

    private static void registrar(Map<Long, Fila> filas, Partido partido) {
        Equipo local = partido.getEquipoLocal();
        Equipo visitante = partido.getEquipoVisitante();
        int golesLocal = partido.getGolesLocal();
        int golesVisitante = partido.getGolesVisitante();
        filas.computeIfAbsent(local.getId(), id -> new Fila(id, local.getNombre()))
                .registrar(golesLocal, golesVisitante);
        filas.computeIfAbsent(visitante.getId(), id -> new Fila(id, visitante.getNombre()))
                .registrar(golesVisitante, golesLocal);
    }

    /** Acumulador mutable de un equipo mientras se recorren los partidos. */
    private static final class Fila {
        private final Long equipoId;
        private final String nombre;
        private int ganados;
        private int empatados;
        private int perdidos;
        private int golesAFavor;
        private int golesEnContra;

        private Fila(Long equipoId, String nombre) {
            this.equipoId = equipoId;
            this.nombre = nombre;
        }

        /** Suma un partido terminado visto desde este equipo. */
        private void registrar(int favor, int contra) {
            golesAFavor += favor;
            golesEnContra += contra;
            if (favor > contra) {
                ganados++;
            } else if (favor == contra) {
                empatados++;
            } else {
                perdidos++;
            }
        }

        private int puntos() {
            return ganados * PUNTOS_VICTORIA + empatados * PUNTOS_EMPATE + perdidos * PUNTOS_DERROTA;
        }

        private int diferenciaDeGol() {
            return golesAFavor - golesEnContra;
        }

        private PosicionEquipoResponse aRespuesta(int posicion) {
            return new PosicionEquipoResponse(posicion, equipoId, nombre, ganados + empatados + perdidos,
                    ganados, empatados, perdidos, golesAFavor, golesEnContra, diferenciaDeGol(), puntos());
        }
    }
}

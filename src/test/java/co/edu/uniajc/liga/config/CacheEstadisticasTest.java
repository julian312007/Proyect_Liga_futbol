package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasEquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasGolRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.GoleadoresService;
import co.edu.uniajc.liga.service.TablaPosicionesService;
import co.edu.uniajc.liga.service.impl.GoleadoresServiceImpl;
import co.edu.uniajc.liga.service.impl.TablaPosicionesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.finalizado;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.gol;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.jugador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caché de la tabla de posiciones y de los goleadores (D-3) con un {@code CacheManager} simple y los servicios
 * reales detrás de su proxy de Spring: la 2.ª llamada no consulta el repositorio y un {@code @CacheEvict}
 * equivalente al de {@code CierrePartidoService.finalizar} (Fase 5) la invalida. Sin Redis, sin base de datos.
 */
@SpringJUnitConfig(CacheEstadisticasTest.Configuracion.class)
class CacheEstadisticasTest {

    private static final long LIGA = 7L;

    @Autowired
    private TablaPosicionesService tablaPosicionesService;
    @Autowired
    private GoleadoresService goleadoresService;
    @Autowired
    private CierreDePartidoDePrueba cierre;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private LigaRepository ligaRepository;
    @Autowired
    private EstadisticasEquipoRepository equipoRepository;
    @Autowired
    private EstadisticasPartidoRepository partidoRepository;
    @Autowired
    private EstadisticasGolRepository golRepository;

    private final Equipo aguilas = equipo(1, "Águilas");
    private final Equipo buhos = equipo(2, "Búhos");

    @BeforeEach
    void limpiar() {
        Mockito.reset(ligaRepository);
        Mockito.reset(equipoRepository);
        Mockito.reset(partidoRepository);
        Mockito.reset(golRepository);
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(equipoRepository.buscarDeLiga(LIGA)).thenReturn(List.of(aguilas, buhos));
        when(partidoRepository.buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO))
                .thenReturn(List.of(finalizado(1, aguilas, 2, buhos, 0)));
        when(golRepository.buscarDeLigaPorEstadoDelPartido(LIGA, EstadoPartido.FINALIZADO))
                .thenReturn(List.of(gol(1, jugador(10, "Ana Ruiz", aguilas), TipoGol.NORMAL)));
    }

    // ------------------------------------------------------ Tabla de posiciones

    @Test
    @DisplayName("Tabla de posiciones: la 2.ª llamada sale de la caché y no consulta el repositorio")
    void tabla_laSegundaLlamadaNoConsultaElRepositorio() {
        List<PosicionEquipoResponse> primera = tablaPosicionesService.obtenerTabla(LIGA);
        List<PosicionEquipoResponse> segunda = tablaPosicionesService.obtenerTabla(LIGA);

        assertThat(segunda).isEqualTo(primera);
        assertThat(primera).extracting(PosicionEquipoResponse::equipo).containsExactly("Águilas", "Búhos");
        verify(partidoRepository, times(1)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
        verify(equipoRepository, times(1)).buscarDeLiga(LIGA);
        verify(ligaRepository, times(1)).existsById(LIGA);
        assertThat(cacheManager.getCache(NombresCache.TABLA_POSICIONES).get(LIGA)).isNotNull();
    }

    @Test
    @DisplayName("Cada liga tiene su propia entrada: pedir otra liga sí consulta el repositorio")
    void tabla_cadaLigaTieneSuClave() {
        when(ligaRepository.existsById(8L)).thenReturn(true);
        when(equipoRepository.buscarDeLiga(8L)).thenReturn(List.of());
        when(partidoRepository.buscarDeLigaPorEstado(8L, EstadoPartido.FINALIZADO)).thenReturn(List.of());

        tablaPosicionesService.obtenerTabla(LIGA);
        tablaPosicionesService.obtenerTabla(8L);
        tablaPosicionesService.obtenerTabla(LIGA);
        tablaPosicionesService.obtenerTabla(8L);

        verify(partidoRepository, times(1)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
        verify(partidoRepository, times(1)).buscarDeLigaPorEstado(8L, EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Invalidación: tras finalizar un partido de la liga, la tabla se recalcula con los datos nuevos")
    void tabla_finalizarUnPartidoLaInvalida() {
        assertThat(tablaPosicionesService.obtenerTabla(LIGA).get(0).pj()).isEqualTo(1);

        // Se cierra otro partido: el repositorio ya devuelve dos partidos finalizados.
        when(partidoRepository.buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO)).thenReturn(List.of(
                finalizado(1, aguilas, 2, buhos, 0), finalizado(2, buhos, 1, aguilas, 1)));
        cierre.finalizar(100L, LIGA);
        List<PosicionEquipoResponse> despues = tablaPosicionesService.obtenerTabla(LIGA);

        assertThat(despues.get(0).pj()).isEqualTo(2);
        assertThat(despues.get(0).pts()).isEqualTo(4);
        verify(partidoRepository, times(2)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Sin invalidación la tabla sigue en caché aunque el repositorio cambie (el TTL es el respaldo)")
    void tabla_sinInvalidarSigueEnCache() {
        tablaPosicionesService.obtenerTabla(LIGA);
        when(partidoRepository.buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO)).thenReturn(List.of());

        assertThat(tablaPosicionesService.obtenerTabla(LIGA).get(0).pj()).isEqualTo(1);
    }

    @Test
    @DisplayName("Finalizar un partido de otra liga no invalida la tabla de esta liga")
    void tabla_finalizarEnOtraLigaNoLaInvalida() {
        tablaPosicionesService.obtenerTabla(LIGA);

        cierre.finalizar(100L, 99L);
        tablaPosicionesService.obtenerTabla(LIGA);

        verify(partidoRepository, times(1)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Una liga inexistente (404) no se guarda en la caché: cada consulta vuelve a comprobarla")
    void tabla_ligaInexistenteNoSeGuardaEnCache() {
        when(ligaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> tablaPosicionesService.obtenerTabla(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> tablaPosicionesService.obtenerTabla(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verify(ligaRepository, times(2)).existsById(99L);
        assertThat(cacheManager.getCache(NombresCache.TABLA_POSICIONES).get(99L)).isNull();
    }

    // ------------------------------------------------------------- Goleadores

    @Test
    @DisplayName("Goleadores: la 2.ª llamada sale de la caché y no consulta el repositorio")
    void goleadores_laSegundaLlamadaNoConsultaElRepositorio() {
        List<GoleadorResponse> primera = goleadoresService.obtenerGoleadores(LIGA);
        List<GoleadorResponse> segunda = goleadoresService.obtenerGoleadores(LIGA);

        assertThat(segunda).isEqualTo(primera);
        assertThat(primera).extracting(GoleadorResponse::jugador).containsExactly("Ana Ruiz");
        verify(golRepository, times(1)).buscarDeLigaPorEstadoDelPartido(LIGA, EstadoPartido.FINALIZADO);
        assertThat(cacheManager.getCache(NombresCache.GOLEADORES).get(LIGA)).isNotNull();
    }

    @Test
    @DisplayName("Invalidación: finalizar un partido invalida a la vez la tabla de posiciones y los goleadores")
    void goleadores_finalizarInvalidaAmbasCaches() {
        tablaPosicionesService.obtenerTabla(LIGA);
        goleadoresService.obtenerGoleadores(LIGA);

        cierre.finalizar(100L, LIGA);
        tablaPosicionesService.obtenerTabla(LIGA);
        goleadoresService.obtenerGoleadores(LIGA);

        verify(partidoRepository, times(2)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
        verify(golRepository, times(2)).buscarDeLigaPorEstadoDelPartido(LIGA, EstadoPartido.FINALIZADO);
    }

    // ---------------------------------------------------------------- Apoyo

    /**
     * Imita a {@code CierrePartidoService.finalizar} (Fase 5): devuelve un resultado con {@code ligaId()} e
     * invalida las dos cachés de esa liga con la misma anotación.
     */
    public static class CierreDePartidoDePrueba {

        @CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#result.ligaId()")
        public ResultadoDePrueba finalizar(Long partidoId, Long ligaId) {
            return new ResultadoDePrueba(partidoId, ligaId);
        }
    }

    /** Equivalente mínimo de {@code ResultadoPartidoResponse}. */
    public record ResultadoDePrueba(Long partidoId, Long ligaId) {
    }

    @Configuration
    @EnableCaching
    static class Configuracion {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);
        }

        @Bean
        LigaRepository ligaRepository() {
            return Mockito.mock(LigaRepository.class);
        }

        @Bean
        EstadisticasEquipoRepository equipoRepository() {
            return Mockito.mock(EstadisticasEquipoRepository.class);
        }

        @Bean
        EstadisticasPartidoRepository partidoRepository() {
            return Mockito.mock(EstadisticasPartidoRepository.class);
        }

        @Bean
        EstadisticasGolRepository golRepository() {
            return Mockito.mock(EstadisticasGolRepository.class);
        }

        @Bean
        TablaPosicionesService tablaPosicionesService(LigaRepository ligas, EstadisticasEquipoRepository equipos,
                                                      EstadisticasPartidoRepository partidos) {
            return new TablaPosicionesServiceImpl(ligas, equipos, partidos);
        }

        @Bean
        GoleadoresService goleadoresService(LigaRepository ligas, EstadisticasGolRepository goles) {
            return new GoleadoresServiceImpl(ligas, goles);
        }

        @Bean
        CierreDePartidoDePrueba cierreDePartidoDePrueba() {
            return new CierreDePartidoDePrueba();
        }
    }
}

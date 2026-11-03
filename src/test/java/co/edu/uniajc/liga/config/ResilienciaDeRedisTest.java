package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.repository.EstadisticasEquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.TablaPosicionesService;
import co.edu.uniajc.liga.service.impl.TablaPosicionesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.finalizado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resiliencia ante un Redis caído (D-3): se usa el {@link RedisCacheManager} real, con la configuración real de
 * {@link RedisCacheConfig}, pero con una fábrica de conexiones que siempre falla. La API debe responder desde la
 * base de datos en lugar de lanzar el error de Redis. No requiere Redis ni Docker.
 */
@SpringJUnitConfig(ResilienciaDeRedisTest.Configuracion.class)
class ResilienciaDeRedisTest {

    private static final long LIGA = 7L;

    @Autowired
    private TablaPosicionesService tablaPosicionesService;
    @Autowired
    private CierreFallidoDePrueba cierre;
    @Autowired
    private RedisConnectionFactory conexiones;
    @Autowired
    private LigaRepository ligaRepository;
    @Autowired
    private EstadisticasEquipoRepository equipoRepository;
    @Autowired
    private EstadisticasPartidoRepository partidoRepository;

    private final Equipo aguilas = equipo(1, "Águilas");
    private final Equipo buhos = equipo(2, "Búhos");

    @BeforeEach
    void preparar() {
        Mockito.reset(ligaRepository);
        Mockito.reset(equipoRepository);
        Mockito.reset(partidoRepository);
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(equipoRepository.buscarDeLiga(LIGA)).thenReturn(List.of(aguilas, buhos));
        when(partidoRepository.buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO))
                .thenReturn(List.of(finalizado(1, aguilas, 2, buhos, 0)));
    }

    @Test
    @DisplayName("Con Redis caído, la tabla de posiciones se responde desde la base de datos en lugar de fallar")
    void redisCaido_laTablaSeResponDeDesdeLaBaseDeDatos() {
        List<PosicionEquipoResponse> tabla = tablaPosicionesService.obtenerTabla(LIGA);

        assertThat(tabla).extracting(PosicionEquipoResponse::equipo).containsExactly("Águilas", "Búhos");
        assertThat(tabla.get(0).pts()).isEqualTo(3);
        // Sí se intentó usar Redis (leer y guardar), y el fallo no llegó al cliente.
        verify(conexiones, Mockito.atLeastOnce()).getConnection();
    }

    @Test
    @DisplayName("Con Redis caído, cada consulta vuelve a la base de datos porque nada se puede guardar")
    void redisCaido_cadaConsultaVuelveALaBaseDeDatos() {
        tablaPosicionesService.obtenerTabla(LIGA);
        tablaPosicionesService.obtenerTabla(LIGA);

        verify(partidoRepository, times(2)).buscarDeLigaPorEstado(LIGA, EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Con Redis caído, finalizar un partido no falla aunque no se pueda invalidar la caché")
    void redisCaido_laInvalidacionNoRompeLaOperacion() {
        assertThatCode(() -> cierre.finalizar(100L, LIGA)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- Apoyo

    /** Imita a {@code CierrePartidoService.finalizar} (Fase 5): invalida las cachés de la liga al terminar. */
    public static class CierreFallidoDePrueba {

        @CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#ligaId")
        public Long finalizar(Long partidoId, Long ligaId) {
            return ligaId;
        }
    }

    @Configuration
    @Import(RedisCacheConfig.class)
    static class Configuracion {

        /** Una fábrica de conexiones a un Redis que no responde. */
        @Bean
        RedisConnectionFactory conexiones() {
            RedisConnectionFactory fabrica = Mockito.mock(RedisConnectionFactory.class);
            when(fabrica.getConnection()).thenThrow(new RedisConnectionFailureException("Redis caído (simulado)"));
            return fabrica;
        }

        /** Arma el manager igual que la autoconfiguración de Spring Boot: configuración base y personalizadores. */
        @Bean
        CacheManager cacheManager(RedisConnectionFactory conexiones, RedisCacheConfiguration base,
                                  RedisCacheManagerBuilderCustomizer personalizador) {
            RedisCacheManager.RedisCacheManagerBuilder constructor =
                    RedisCacheManager.builder(conexiones).cacheDefaults(base);
            personalizador.customize(constructor);
            return constructor.build();
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
        TablaPosicionesService tablaPosicionesService(LigaRepository ligas, EstadisticasEquipoRepository equipos,
                                                      EstadisticasPartidoRepository partidos) {
            return new TablaPosicionesServiceImpl(ligas, equipos, partidos);
        }

        @Bean
        CierreFallidoDePrueba cierreFallidoDePrueba() {
            return new CierreFallidoDePrueba();
        }
    }
}

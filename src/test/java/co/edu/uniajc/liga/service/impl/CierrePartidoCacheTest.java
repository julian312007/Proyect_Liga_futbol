package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.ResultadoPartidoMapper;
import co.edu.uniajc.liga.mapper.ResultadoPartidoMapperImpl;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.service.CierrePartidoService;
import co.edu.uniajc.liga.support.FabricaEventosPartido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba, con un contexto de Spring mínimo (solo {@code @EnableCaching} y una caché en memoria; sin
 * Redis, sin base de datos y sin Docker), que finalizar un partido invalida la tabla de posiciones y los
 * goleadores de SU liga y de ninguna otra, y que un intento fallido (RN-11) no invalida nada.
 *
 * <p>La Fase 6 pone la caché real en Redis; aquí se prueba la anotación {@code @CacheEvict} del servicio.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CierrePartidoCacheTest.Configuracion.class)
class CierrePartidoCacheTest {

    private static final Long LIGA = 10L;
    private static final Long OTRA_LIGA = 11L;
    private static final Long PARTIDO_ID = 50L;

    @Configuration
    @EnableCaching
    static class Configuracion {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);
        }

        @Bean
        PartidoEventosRepository partidoEventosRepository() {
            return Mockito.mock(PartidoEventosRepository.class);
        }

        @Bean
        GolRepository golRepository() {
            return Mockito.mock(GolRepository.class);
        }

        @Bean
        ResultadoPartidoMapper resultadoPartidoMapper() {
            return new ResultadoPartidoMapperImpl();
        }

        @Bean
        CierrePartidoService cierrePartidoService(PartidoEventosRepository partidoRepository,
                                                  GolRepository golRepository,
                                                  ResultadoPartidoMapper mapper) {
            return new CierrePartidoServiceImpl(partidoRepository, golRepository, mapper);
        }
    }

    @Autowired
    private CierrePartidoService servicio;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PartidoEventosRepository partidoRepository;
    @Autowired
    private GolRepository golRepository;

    @BeforeEach
    void preparar() {
        Mockito.reset(partidoRepository);
        Mockito.reset(golRepository);
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
        for (String nombre : List.of(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES)) {
            cacheManager.getCache(nombre).put(LIGA, "datos viejos de la liga " + LIGA);
            cacheManager.getCache(nombre).put(OTRA_LIGA, "datos de la liga " + OTRA_LIGA);
        }
    }

    private Partido partidoEnJuego() {
        Equipo local = FabricaEventosPartido.equipo(1L, "Local FC");
        Equipo visitante = FabricaEventosPartido.equipo(2L, "Visitante FC");
        Partido partido = FabricaEventosPartido.partido(PARTIDO_ID, FabricaEventosPartido.liga(LIGA), local, visitante,
                EstadoPartido.EN_JUEGO, Instant.parse("2026-02-01T20:00:00Z"));
        Mockito.when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        Mockito.when(golRepository.findConEquipoByPartidoId(PARTIDO_ID)).thenReturn(List.of());
        return partido;
    }

    @Test
    @DisplayName("D-3: finalizar invalida la tabla de posiciones y los goleadores de la liga del partido, y solo esa")
    void finalizar_invalidaLasDosCachesDeSuLiga() {
        partidoEnJuego();

        servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(0, 0));

        for (String nombre : List.of(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES)) {
            assertThat(cacheManager.getCache(nombre).get(LIGA)).as("%s de la liga %d", nombre, LIGA).isNull();
            assertThat(cacheManager.getCache(nombre).get(OTRA_LIGA)).as("%s de otra liga", nombre).isNotNull();
        }
    }

    @Test
    @DisplayName("D-3: si la finalización falla (RN-11) la caché NO se invalida")
    void finalizarFallido_noInvalidaLaCache() {
        partidoEnJuego();

        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(3, 0)))
                .isInstanceOf(ReglaNegocioException.class);

        for (String nombre : List.of(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES)) {
            assertThat(cacheManager.getCache(nombre).get(LIGA)).as("%s sigue en caché", nombre).isNotNull();
        }
    }
}

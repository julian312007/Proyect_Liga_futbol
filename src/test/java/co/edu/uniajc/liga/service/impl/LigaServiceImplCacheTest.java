package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.mapper.LigaMapper;
import co.edu.uniajc.liga.mapper.LigaMapperImpl;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.LigaService;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Efecto real del {@code @CacheEvict} de {@code LigaServiceImpl.eliminar}, con un contexto de Spring mínimo
 * (solo el proxy de cachés y un {@code ConcurrentMapCacheManager}; sin Boot, sin Redis, sin Docker): al
 * eliminar una liga se invalidan sus entradas en las dos cachés y no las de otras ligas; si la eliminación
 * falla (409) no se invalida nada.
 */
class LigaServiceImplCacheTest {

    @Configuration
    @EnableCaching
    static class Configuracion {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);
        }

        @Bean
        LigaRepository ligaRepository() {
            return mock(LigaRepository.class);
        }

        @Bean
        VerificadoresEliminacion verificadoresEliminacion() {
            return mock(VerificadoresEliminacion.class);
        }

        @Bean
        LigaMapper ligaMapper() {
            return new LigaMapperImpl();
        }

        @Bean
        LigaService ligaService(LigaRepository ligaRepository, LigaMapper ligaMapper,
                                VerificadoresEliminacion verificadoresEliminacion) {
            return new LigaServiceImpl(ligaRepository, ligaMapper, verificadoresEliminacion);
        }
    }

    private static Liga liga(Long id) {
        Liga liga = new Liga();
        liga.setId(id);
        liga.setNombre("Liga BetPlay");
        liga.setTemporada("2026-I");
        liga.setFechaInicio(LocalDate.of(2026, 2, 1));
        liga.setFechaFin(LocalDate.of(2026, 6, 30));
        return liga;
    }

    private static void llenarCaches(CacheManager caches) {
        caches.getCache(NombresCache.TABLA_POSICIONES).put(7L, "tabla de la liga 7");
        caches.getCache(NombresCache.TABLA_POSICIONES).put(8L, "tabla de la liga 8");
        caches.getCache(NombresCache.GOLEADORES).put(7L, "goleadores de la liga 7");
        caches.getCache(NombresCache.GOLEADORES).put(8L, "goleadores de la liga 8");
    }

    @Test
    @DisplayName("Eliminar la liga 7 invalida su tabla de posiciones y sus goleadores, y deja intactos los de la liga 8")
    void eliminar_invalidaLasCachesDeEsaLiga() {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            CacheManager caches = contexto.getBean(CacheManager.class);
            when(contexto.getBean(LigaRepository.class).findById(7L)).thenReturn(Optional.of(liga(7L)));
            llenarCaches(caches);

            contexto.getBean(LigaService.class).eliminar(7L);

            assertThat(caches.getCache(NombresCache.TABLA_POSICIONES).get(7L)).isNull();
            assertThat(caches.getCache(NombresCache.GOLEADORES).get(7L)).isNull();
            assertThat(caches.getCache(NombresCache.TABLA_POSICIONES).get(8L)).isNotNull();
            assertThat(caches.getCache(NombresCache.GOLEADORES).get(8L)).isNotNull();
        }
    }

    @Test
    @DisplayName("Si la eliminación falla con 409, las cachés no se invalidan (la liga sigue existiendo)")
    void eliminarFallido_noInvalidaLasCaches() {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            CacheManager caches = contexto.getBean(CacheManager.class);
            when(contexto.getBean(LigaRepository.class).findById(7L)).thenReturn(Optional.of(liga(7L)));
            doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "La liga tiene equipos"))
                    .when(contexto.getBean(VerificadoresEliminacion.class)).verificar(any(), any());
            llenarCaches(caches);

            assertThatThrownBy(() -> contexto.getBean(LigaService.class).eliminar(7L))
                    .isInstanceOf(ConflictoException.class);

            assertThat(caches.getCache(NombresCache.TABLA_POSICIONES).get(7L)).isNotNull();
            assertThat(caches.getCache(NombresCache.GOLEADORES).get(7L)).isNotNull();
        }
    }
}

package co.edu.uniajc.liga.config;

/**
 * Nombres de las cachés de Redis (D-3). Son constantes porque los valores de las anotaciones
 * {@code @Cacheable} y {@code @CacheEvict} deben serlo. En ambas cachés la clave es el id de la liga.
 *
 * <p>La Fase 6 activa el almacenamiento ({@code @EnableCaching}, {@code RedisCacheConfig}). Hasta entonces
 * las anotaciones que ya usan estos nombres no hacen nada.
 */
public final class NombresCache {

    public static final String TABLA_POSICIONES = "tablaPosiciones";
    public static final String GOLEADORES = "goleadores";

    private NombresCache() {
    }
}

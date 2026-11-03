package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Caché de la API con Redis (D-3): activa {@code @EnableCaching} y define cómo se guardan las entradas.
 *
 * <ul>
 *   <li><b>TTL de 10 minutos</b>: respaldo si una invalidación ({@code @CacheEvict}) se pierde.</li>
 *   <li><b>Prefijo de claves {@code liga:}</b>: la clave en Redis queda, por ejemplo, {@code liga:tablaPosiciones::7}
 *       (nombre de la caché y id de la liga).</li>
 *   <li><b>Serialización JSON</b> con Jackson 3 ({@link JacksonJsonRedisSerializer}), no la serialización de Java:
 *       se guardan DTO de respuesta, nunca entidades JPA. Cada caché declara el tipo exacto que guarda, sin
 *       información de tipos dentro del JSON, así que leer una entrada no puede instanciar clases arbitrarias.</li>
 *   <li><b>Sin valores {@code null}</b>: lo que no existe no se guarda.</li>
 *   <li><b>Fallos de Redis</b>: {@link RedisCacheErrorHandler} los registra y deja pasar a la base de datos.</li>
 * </ul>
 *
 * <p>No define el {@code CacheManager}: lo crea la autoconfiguración de Spring Boot, que usa las dos piezas de
 * abajo. Por eso, con {@code spring.cache.type=simple} (perfil de pruebas) Boot usa una caché en memoria y Redis
 * no interviene. Para <b>agregar una caché nueva</b> hay que añadir su nombre a {@link NombresCache} y registrar
 * aquí el tipo que guarda ({@link #registrarCaches}); una caché sin registrar falla en vez de usar un formato
 * indefinido, y {@code RedisCacheConfigTest} avisa si se olvida.
 *
 * <p>Orden de los proxies: el de la caché se ubica por fuera del de las transacciones. Así, una lectura que
 * acierta en la caché no abre transacción y, sobre todo, un {@code @CacheEvict} sobre un método transaccional
 * (cerrar un partido, eliminar una liga) invalida <em>después</em> de confirmar la transacción; si invalidara
 * antes, otra solicitud podría volver a llenar la caché con datos anteriores a la confirmación.
 */
@Configuration
@EnableCaching(order = RedisCacheConfig.ORDEN_DEL_PROXY_DE_CACHE)
public class RedisCacheConfig implements CachingConfigurer {

    /** Tiempo de vida de cada entrada. */
    public static final Duration TTL = Duration.ofMinutes(10);

    /** Prefijo de todas las claves de la caché en Redis. */
    public static final String PREFIJO_DE_CLAVES = "liga:";

    /** Un número menor se ejecuta antes (más afuera) que el proxy transaccional, que usa el valor más alto. */
    static final int ORDEN_DEL_PROXY_DE_CACHE = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * Mapeador de las entradas. Ignora propiedades desconocidas para que un DTO al que se le agrega un campo no
     * haga fallar la lectura de entradas escritas por una versión anterior (vencen solas por el TTL).
     */
    private static final JsonMapper MAPEADOR = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }

    /** Configuración base de toda caché: TTL, prefijo, claves de texto, valores en JSON y sin {@code null}. */
    @Bean
    static RedisCacheConfiguration configuracionBaseDeCache() {
        return configuracionBase();
    }

    /** Registra las cachés conocidas con el tipo que guarda cada una. Boot lo aplica al construir el manager. */
    @Bean
    static RedisCacheManagerBuilderCustomizer cachesDeLaLiga(RedisCacheConfiguration configuracionBase) {
        return constructor -> registrarCaches(constructor, configuracionBase);
    }

    /**
     * Si Redis se desconecta, los comandos se rechazan al instante en vez de quedar en cola hasta agotar el
     * tiempo de espera (60 s por defecto en Lettuce); así {@link RedisCacheErrorHandler} responde desde la base
     * de datos sin hacer esperar al cliente. No cambia nada mientras Redis responde.
     */
    @Bean
    static LettuceClientOptionsBuilderCustomizer rechazarComandosSinConexion() {
        return opciones -> opciones.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    static RedisCacheConfiguration configuracionBase() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .prefixCacheNameWith(PREFIJO_DE_CLAVES)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(MAPEADOR, Object.class)));
    }

    static void registrarCaches(RedisCacheManagerBuilder constructor, RedisCacheConfiguration base) {
        constructor
                .disableCreateOnMissingCache()
                .withCacheConfiguration(NombresCache.TABLA_POSICIONES,
                        base.serializeValuesWith(paraListaDe(PosicionEquipoResponse.class)))
                .withCacheConfiguration(NombresCache.GOLEADORES,
                        base.serializeValuesWith(paraListaDe(GoleadorResponse.class)));
    }

    /** Serializador JSON de una {@code List<T>} con el tipo de elemento exacto. */
    private static <T> SerializationPair<List<T>> paraListaDe(Class<T> elemento) {
        JavaType tipo = MAPEADOR.getTypeFactory().constructCollectionType(List.class, elemento);
        return SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<List<T>>(MAPEADOR, tipo));
    }
}

package co.edu.uniajc.liga.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Hace que un fallo de la caché (Redis caído, tiempo de espera agotado, valor que ya no se puede leer) no
 * tumbe la API: registra el problema en nivel {@code WARN} y deja que la operación siga. Así, ante un fallo de
 * lectura el método del servicio se ejecuta y responde desde la base de datos (D-3).
 *
 * <p>Pro: la API sigue viva aunque Redis no esté. Contra: puede ocultar el problema, por eso cada fallo se
 * registra con el nombre de la caché y la clave. Un fallo al <em>invalidar</em> deja el dato viejo en la caché
 * hasta que venza su TTL (10 minutos), que es el respaldo previsto para las invalidaciones perdidas.
 */
@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        registrar("leer", exception, cache, key, "Se consulta la base de datos.");
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
        registrar("guardar", exception, cache, key, "La respuesta se entrega sin guardarla en la caché.");
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        registrar("invalidar", exception, cache, key,
                "La entrada puede quedar desactualizada hasta que venza su TTL.");
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        registrar("vaciar", exception, cache, null,
                "Las entradas pueden quedar desactualizadas hasta que venza su TTL.");
    }

    private static void registrar(String operacion, RuntimeException exception, Cache cache,
                                  @Nullable Object clave, String consecuencia) {
        log.warn("Falló la caché al {} (caché '{}', clave '{}'): {}: {}. {}", operacion, cache.getName(), clave,
                exception.getClass().getSimpleName(), exception.getMessage(), consecuencia);
        // La traza completa solo en DEBUG: con Redis caído se repetiría en cada solicitud.
        log.debug("Detalle del fallo de la caché", exception);
    }
}

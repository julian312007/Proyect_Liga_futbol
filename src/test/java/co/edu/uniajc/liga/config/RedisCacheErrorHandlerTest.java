package co.edu.uniajc.liga.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RedisCacheErrorHandler}: un fallo de la caché no se propaga (la operación sigue con la base de datos)
 * y queda registrado en nivel {@code WARN} con el nombre de la caché y la clave.
 */
class RedisCacheErrorHandlerTest {

    private final RedisCacheErrorHandler manejador = new RedisCacheErrorHandler();
    private final RuntimeException redisCaido = new RedisConnectionFailureException("Redis no responde");
    private final Cache cache = mock(Cache.class);

    private Logger registro;
    private ListAppender<ILoggingEvent> eventos;

    @BeforeEach
    void capturarElRegistro() {
        when(cache.getName()).thenReturn("tablaPosiciones");
        registro = (Logger) LoggerFactory.getLogger(RedisCacheErrorHandler.class);
        eventos = new ListAppender<>();
        eventos.start();
        registro.addAppender(eventos);
    }

    @AfterEach
    void soltarElRegistro() {
        registro.detachAppender(eventos);
    }

    @Test
    @DisplayName("Un fallo al leer no se propaga y se registra en WARN con la caché, la clave y el motivo")
    void fallaAlLeer_noSePropagaYSeRegistraEnWarn() {
        assertThatCode(() -> manejador.handleCacheGetError(redisCaido, cache, 7L)).doesNotThrowAnyException();

        List<ILoggingEvent> warn = eventosEnWarn();
        assertThat(warn).hasSize(1);
        assertThat(warn.get(0).getFormattedMessage())
                .contains("leer")
                .contains("tablaPosiciones")
                .contains("7")
                .contains("RedisConnectionFailureException")
                .contains("Redis no responde")
                .contains("base de datos");
    }

    @Test
    @DisplayName("Un fallo al guardar no se propaga y se registra en WARN")
    void fallaAlGuardar_noSePropagaYSeRegistraEnWarn() {
        assertThatCode(() -> manejador.handleCachePutError(redisCaido, cache, 7L, List.of("valor")))
                .doesNotThrowAnyException();

        assertThat(eventosEnWarn()).hasSize(1);
        assertThat(eventosEnWarn().get(0).getFormattedMessage()).contains("guardar").contains("tablaPosiciones");
    }

    @Test
    @DisplayName("Un fallo al invalidar no se propaga, se registra en WARN y avisa que el dato puede quedar viejo hasta el TTL")
    void fallaAlInvalidar_noSePropagaYSeRegistraEnWarn() {
        assertThatCode(() -> manejador.handleCacheEvictError(redisCaido, cache, 7L)).doesNotThrowAnyException();

        assertThat(eventosEnWarn()).hasSize(1);
        assertThat(eventosEnWarn().get(0).getFormattedMessage()).contains("invalidar").contains("TTL");
    }

    @Test
    @DisplayName("Un fallo al vaciar la caché no se propaga y se registra en WARN")
    void fallaAlVaciar_noSePropagaYSeRegistraEnWarn() {
        assertThatCode(() -> manejador.handleCacheClearError(redisCaido, cache)).doesNotThrowAnyException();

        assertThat(eventosEnWarn()).hasSize(1);
        assertThat(eventosEnWarn().get(0).getFormattedMessage()).contains("vaciar").contains("tablaPosiciones");
    }

    @Test
    @DisplayName("Un valor que ya no se puede leer (por ejemplo, un DTO que cambió) también cae a la base de datos")
    void fallaDeDeserializacion_noSePropaga() {
        RuntimeException ilegible = new IllegalStateException("No se pudo leer el JSON");

        assertThatCode(() -> manejador.handleCacheGetError(ilegible, cache, 7L)).doesNotThrowAnyException();

        assertThat(eventosEnWarn()).hasSize(1);
    }

    private List<ILoggingEvent> eventosEnWarn() {
        return eventos.list.stream().filter(evento -> evento.getLevel() == Level.WARN).toList();
    }
}

package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Configuración de la caché de Redis (D-3): TTL, prefijo de claves, serialización JSON y registro de las cachés.
 * No necesita Redis: se construye el {@link RedisCacheManager} con una fábrica de conexiones simulada y se
 * prueban los serializadores directamente.
 */
class RedisCacheConfigTest {

    private final RedisCacheManager manager = construirManager();

    // ------------------------------------------------------------- Valores base

    @Test
    @DisplayName("La configuración base fija TTL de 10 minutos, prefijo \"liga:\" y no guarda valores nulos")
    void configuracionBase_fijaTtlPrefijoYSinNulos() {
        RedisCacheConfiguration base = RedisCacheConfig.configuracionBase();

        assertThat(base.getTtlFunction().getTimeToLive("7", List.of())).isEqualTo(Duration.ofMinutes(10));
        assertThat(base.getKeyPrefixFor(NombresCache.TABLA_POSICIONES)).isEqualTo("liga:tablaPosiciones::");
        assertThat(base.getAllowCacheNullValues()).isFalse();
        assertThat(base.usePrefix()).isTrue();
    }

    @Test
    @DisplayName("Ninguna caché usa la serialización de Java: los valores se guardan como JSON")
    void ningunaCacheUsaSerializacionDeJava() {
        for (Map.Entry<String, RedisCacheConfiguration> entrada : manager.getCacheConfigurations().entrySet()) {
            ByteBuffer bytes = entrada.getValue().getValueSerializationPair().write(List.of());

            // La serialización de Java empieza con 0xACED; el JSON de una lista, con '['.
            assertThat((char) bytes.get(0)).as("primer byte de la caché %s", entrada.getKey()).isEqualTo('[');
        }
    }

    // ----------------------------------------------------- Cachés registradas

    @Test
    @DisplayName("Cada nombre de NombresCache está registrado con TTL de 10 minutos y prefijo \"liga:\"")
    void todoNombreDeNombresCache_estaRegistrado() throws IllegalAccessException {
        List<String> nombres = new ArrayList<>();
        for (Field campo : NombresCache.class.getDeclaredFields()) {
            if (Modifier.isStatic(campo.getModifiers()) && campo.getType() == String.class) {
                nombres.add((String) campo.get(null));
            }
        }
        assertThat(nombres).as("nombres de caché declarados").containsExactlyInAnyOrder(
                NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);

        Map<String, RedisCacheConfiguration> configuraciones = manager.getCacheConfigurations();
        // Si alguien agrega un nombre a NombresCache y olvida registrarlo en RedisCacheConfig, esta prueba falla.
        assertThat(configuraciones.keySet()).containsExactlyInAnyOrderElementsOf(nombres);
        configuraciones.forEach((nombre, configuracion) -> {
            assertThat(configuracion.getTtlFunction().getTimeToLive("7", List.of()))
                    .as("TTL de %s", nombre).isEqualTo(Duration.ofMinutes(10));
            assertThat(configuracion.getKeyPrefixFor(nombre)).isEqualTo("liga:" + nombre + "::");
        });
    }

    @Test
    @DisplayName("Una caché no registrada no se crea sola con un formato indefinido")
    void cacheNoRegistrada_noSeCreaSola() {
        assertThat(manager.getCache("estadios")).isNull();
        assertThat(manager.getCache(NombresCache.TABLA_POSICIONES)).isNotNull();
        assertThat(manager.getCache(NombresCache.GOLEADORES)).isNotNull();
    }

    // ------------------------------------------------------- Serialización JSON

    @Test
    @DisplayName("La tabla de posiciones se guarda como JSON y se lee de vuelta como la misma lista de DTO")
    void tablaDePosiciones_idaYVueltaEnJson() {
        List<PosicionEquipoResponse> tabla = List.of(
                new PosicionEquipoResponse(1, 1L, "Águilas", 2, 1, 1, 0, 3, 1, 2, 4),
                new PosicionEquipoResponse(2, 2L, "Búhos", 2, 1, 0, 1, 3, 3, 0, 3));
        var par = manager.getCacheConfigurations().get(NombresCache.TABLA_POSICIONES).getValueSerializationPair();

        ByteBuffer bytes = par.write(tabla);
        String json = texto(bytes);
        Object leido = par.read(bytes);

        assertThat(json).startsWith("[{").contains("\"equipo\":\"Águilas\"").contains("\"pts\":4").contains("\"dg\":2");
        assertThat(json).doesNotContain("@class").doesNotContain("co.edu.uniajc");
        assertThat(leido).isEqualTo(tabla);
        assertThat(((List<?>) leido).get(0)).isInstanceOf(PosicionEquipoResponse.class);
    }

    @Test
    @DisplayName("Los goleadores se guardan como JSON y se leen de vuelta como la misma lista de DTO")
    void goleadores_idaYVueltaEnJson() {
        List<GoleadorResponse> goleadores = List.of(new GoleadorResponse(1, 10L, "Ana Ruiz", 1L, "Águilas", 3));
        var par = manager.getCacheConfigurations().get(NombresCache.GOLEADORES).getValueSerializationPair();

        ByteBuffer bytes = par.write(goleadores);
        Object leido = par.read(bytes);

        assertThat(leido).isEqualTo(goleadores);
        assertThat(((List<?>) leido).get(0)).isInstanceOf(GoleadorResponse.class);
    }

    @Test
    @DisplayName("Una lista vacía también se guarda y se lee (una liga sin partidos no es un error)")
    void listaVacia_idaYVuelta() {
        var par = manager.getCacheConfigurations().get(NombresCache.TABLA_POSICIONES).getValueSerializationPair();

        Object leido = par.read(par.write(List.of()));

        assertThat(leido).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Una entrada escrita con un campo que el DTO ya no tiene (o con uno nuevo) se lee sin fallar")
    void entradaConCamposDistintos_seLeeSinFallar() {
        var par = manager.getCacheConfigurations().get(NombresCache.GOLEADORES).getValueSerializationPair();
        String jsonDeOtraVersion = "[{\"posicion\":1,\"jugadorId\":10,\"jugador\":\"Ana Ruiz\",\"equipoId\":1,"
                + "\"equipo\":\"Águilas\",\"goles\":3,\"campoQueYaNoExiste\":true}]";

        Object leido = par.read(ByteBuffer.wrap(jsonDeOtraVersion.getBytes(StandardCharsets.UTF_8)));

        assertThat(leido).isEqualTo(List.of(new GoleadorResponse(1, 10L, "Ana Ruiz", 1L, "Águilas", 3)));
    }

    // ------------------------------------------------------- Manejo de errores

    @Test
    @DisplayName("La configuración de caché usa RedisCacheErrorHandler para que un fallo de Redis no tumbe la API")
    void errorHandler_esElQueRegistraYDejaPasar() {
        assertThat(new RedisCacheConfig().errorHandler()).isInstanceOf(RedisCacheErrorHandler.class);
    }

    // ----------------------------------------------------------------- Apoyo

    private static RedisCacheManager construirManager() {
        RedisCacheConfiguration base = RedisCacheConfig.configuracionBase();
        RedisCacheManager.RedisCacheManagerBuilder constructor =
                RedisCacheManager.builder(mock(RedisConnectionFactory.class)).cacheDefaults(base);
        RedisCacheConfig.registrarCaches(constructor, base);
        RedisCacheManager manager = constructor.build();
        manager.afterPropertiesSet();
        return manager;
    }

    private static String texto(ByteBuffer bytes) {
        return StandardCharsets.UTF_8.decode(bytes.duplicate()).toString();
    }
}

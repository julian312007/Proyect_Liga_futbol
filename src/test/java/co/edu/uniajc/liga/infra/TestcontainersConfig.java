package co.edu.uniajc.liga.infra;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contenedores reales de PostgreSQL y Redis para las pruebas de integración (requieren Docker).
 * Las mismas imágenes que usa {@code docker-compose.yml}. {@code @ServiceConnection} hace que
 * Spring Boot apunte la aplicación a estos contenedores sin tocar ninguna propiedad.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * Margen de arranque de los contenedores. El valor por defecto de Testcontainers (60 s) no alcanza
     * en equipos con poca memoria, donde {@code initdb} de PostgreSQL tarda más de un minuto aunque
     * termine bien (se observó en Windows con 6 GB de RAM).
     */
    private static final Duration TIEMPO_ARRANQUE = Duration.ofMinutes(3);

    @Bean
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    JdbcDatabaseContainer<?> postgres() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withStartupTimeout(TIEMPO_ARRANQUE);
        return postgres;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withStartupTimeout(TIEMPO_ARRANQUE);
    }
}

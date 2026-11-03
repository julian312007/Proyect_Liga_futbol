package co.edu.uniajc.liga.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo de la Fase 0 (criterio de terminado): la aplicación arranca, se conecta a
 * PostgreSQL, Flyway aplica la línea base y Redis responde. Requiere Docker; corre en {@code mvn verify}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class InfraestructuraIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("La aplicación se conecta a PostgreSQL y Flyway aplicó la migración V1")
    void conectaAPostgresYFlywayAplicoLaLineaBase() throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             Statement consulta = conexion.createStatement();
             ResultSet resultado = consulta.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("La aplicación se conecta a Redis y responde PONG")
    void conectaARedis() {
        try (RedisConnection conexion = redisConnectionFactory.getConnection()) {
            assertThat(String.valueOf(conexion.ping())).isEqualToIgnoringCase("PONG");
        }
    }
}

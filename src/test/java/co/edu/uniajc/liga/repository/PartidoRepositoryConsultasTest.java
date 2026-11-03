package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Valida contra el metamodelo real de Hibernate (sin base de datos ni Docker) las consultas {@code @Query} de
 * {@link PartidoRepository} y los filtros de {@link PartidoEspecificaciones}. Una consulta JPQL mal escrita
 * hace que la aplicación entera falle al arrancar; esta prueba lo detecta antes. Solo comprueba que las
 * consultas se interpretan (nombres de propiedades, tipos, sintaxis); su resultado se prueba en
 * {@code PartidosIT}, que sí usa PostgreSQL.
 */
class PartidoRepositoryConsultasTest {

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void abrirSessionFactory() {
        // Sin URL de conexión y sin acceso a los metadatos JDBC: Hibernate arranca sin tocar ninguna base de datos.
        StandardServiceRegistry registro = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        Metadata metadata = new MetadataSources(registro)
                .addAnnotatedClass(Liga.class)
                .addAnnotatedClass(Estadio.class)
                .addAnnotatedClass(Entrenador.class)
                .addAnnotatedClass(Arbitro.class)
                .addAnnotatedClass(Equipo.class)
                .addAnnotatedClass(Partido.class)
                .buildMetadata();
        sessionFactory = metadata.buildSessionFactory();
    }

    @AfterAll
    static void cerrarSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("Cada consulta @Query de PartidoRepository (RN-05 a RN-07) es JPQL válido para el modelo")
    void consultasJpql_sonValidas() {
        List<Method> conConsulta = Arrays.stream(PartidoRepository.class.getDeclaredMethods())
                .filter(metodo -> metodo.isAnnotationPresent(Query.class))
                .toList();
        // Si alguien agrega o quita una consulta, que esta prueba se entere de que cambió el conjunto.
        assertThat(conConsulta).extracting(Method::getName)
                .containsExactlyInAnyOrder("contarPartidosDelEquipoEnJornada",
                        "horariosOcupadosDelEstadio", "horariosOcupadosDelArbitro");

        try (Session sesion = sessionFactory.openSession()) {
            for (Method metodo : conConsulta) {
                String jpql = metodo.getAnnotation(Query.class).value();
                // Object.class: cualquier resultado (Long, Instant...) es asignable a Object.
                assertThatCode(() -> sesion.createSelectionQuery(jpql, Object.class))
                        .as("consulta de %s", metodo.getName())
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("El calendario (F8) con los cuatro filtros usa propiedades que existen en Partido")
    void especificacionConTodosLosFiltros_seConstruye() {
        assertThatCode(() -> construir(PartidoEspecificaciones.calendario(1L, 3, 10L, 20L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El calendario (F8) sin filtros trae todo y también se construye")
    void especificacionSinFiltros_seConstruye() {
        assertThatCode(() -> construir(PartidoEspecificaciones.calendario(null, null, null, null)))
                .doesNotThrowAnyException();
    }

    private static void construir(org.springframework.data.jpa.domain.Specification<Partido> especificacion) {
        try (Session sesion = sessionFactory.openSession()) {
            CriteriaBuilder cb = sesion.getCriteriaBuilder();
            CriteriaQuery<Partido> consulta = cb.createQuery(Partido.class);
            Root<Partido> raiz = consulta.from(Partido.class);
            consulta.select(raiz).where(especificacion.toPredicate(raiz, consulta, cb));
            // Crear la consulta ejecuta el análisis semántico completo (sin enviar nada a la base de datos).
            sesion.createQuery(consulta);
        }
    }
}

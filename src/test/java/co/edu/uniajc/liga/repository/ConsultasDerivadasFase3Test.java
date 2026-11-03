package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Red de seguridad sin base de datos para los repositorios de la Fase 3: Spring Data deriva las consultas del
 * nombre del método al arrancar y un nombre mal escrito (por ejemplo, una propiedad que no existe) hace fallar
 * todo el contexto. Aquí se analiza cada nombre con el mismo {@link PartTree} que usa Spring Data y se comprueba
 * que las propiedades de los {@link EntityGraph} existan en la entidad. Complementa (no sustituye) a
 * {@code EquiposJugadoresIT}, que sí ejecuta las consultas contra PostgreSQL.
 */
class ConsultasDerivadasFase3Test {

    @Test
    @DisplayName("Los métodos derivados de EquipoRepository se resuelven contra las propiedades de Equipo")
    void equipoRepository_metodosDerivadosSeResuelven() {
        List<String> nombres = metodosDerivados(EquipoRepository.class);

        assertThat(nombres).hasSizeGreaterThanOrEqualTo(6);
        nombres.forEach(nombre -> assertThatCode(() -> new PartTree(nombre, Equipo.class))
                .as("método %s", nombre)
                .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("Los métodos derivados de JugadorRepository se resuelven contra las propiedades de Jugador")
    void jugadorRepository_metodosDerivadosSeResuelven() {
        List<String> nombres = metodosDerivados(JugadorRepository.class);

        assertThat(nombres).hasSizeGreaterThanOrEqualTo(4);
        nombres.forEach(nombre -> assertThatCode(() -> new PartTree(nombre, Jugador.class))
                .as("método %s", nombre)
                .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("Un nombre de propiedad inexistente sí hace fallar el análisis (la prueba detecta errores)")
    void nombreInexistente_haceFallarElAnalisis() {
        assertThatCode(() -> new PartTree("existsByLigaNombreInventado", Equipo.class))
                .isInstanceOf(PropertyReferenceException.class);
    }

    @Test
    @DisplayName("Las propiedades de los @EntityGraph existen en las entidades")
    void entityGraphs_usanPropiedadesExistentes() throws Exception {
        assertThat(propiedadesDeEntityGraph(EquipoRepository.class))
                .isNotEmpty()
                .allSatisfy(propiedad -> assertThat(Arrays.stream(Equipo.class.getDeclaredFields())
                        .map(campo -> campo.getName())).contains(propiedad));
        assertThat(propiedadesDeEntityGraph(JugadorRepository.class))
                .isNotEmpty()
                .allSatisfy(propiedad -> assertThat(Arrays.stream(Jugador.class.getDeclaredFields())
                        .map(campo -> campo.getName())).contains(propiedad));
    }

    // ------------------------------------------------------------- Apoyo

    /** Nombres de los métodos de consulta derivada declarados en la interfaz (no los CRUD redeclarados). */
    private static List<String> metodosDerivados(Class<?> repositorio) {
        return Arrays.stream(repositorio.getDeclaredMethods())
                .map(Method::getName)
                .filter(nombre -> nombre.startsWith("existsBy") || nombre.startsWith("findBy")
                        || nombre.startsWith("countBy"))
                .distinct()
                .toList();
    }

    private static List<String> propiedadesDeEntityGraph(Class<?> repositorio) {
        return Arrays.stream(repositorio.getDeclaredMethods())
                .map(metodo -> metodo.getAnnotation(EntityGraph.class))
                .filter(grafo -> grafo != null)
                .flatMap(grafo -> Arrays.stream(grafo.attributePaths()))
                .distinct()
                .toList();
    }
}

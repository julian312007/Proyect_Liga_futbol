package co.edu.uniajc.liga.dto;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.request.LigaRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validación (Bean Validation) de los cuerpos de entrada de los catálogos, sin Spring: EST-03 (capacidad mayor
 * que cero y ciudad obligatoria) y las restricciones de longitud que coinciden con las columnas de las migraciones.
 */
class RequestsCatalogoValidacionTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static <T> Set<String> camposInvalidos(T request) {
        return validador.validate(request).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static <T> String mensajeDe(T request, String campo) {
        return validador.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------------ Estadio (EST-03)

    @Test
    @DisplayName("EST-03 positivo: un estadio con ciudad y capacidad 1 (el mínimo) es válido")
    void estadio_valido() {
        assertThat(camposInvalidos(new EstadioRequest("Pascual Guerrero", "Cali", 1))).isEmpty();
        assertThat(camposInvalidos(new EstadioRequest("Pascual Guerrero", "Cali", 38_000))).isEmpty();
    }

    @ParameterizedTest(name = "capacidad {0}")
    @ValueSource(ints = {0, -1, -38_000})
    @DisplayName("EST-03 negativo: capacidad cero o negativa es inválida")
    void estadio_capacidadNoPositiva(int capacidad) {
        EstadioRequest request = new EstadioRequest("Pascual Guerrero", "Cali", capacidad);

        assertThat(camposInvalidos(request)).containsExactly("capacidad");
        assertThat(mensajeDe(request, "capacidad")).isEqualTo("La capacidad debe ser mayor que cero");
    }

    @Test
    @DisplayName("EST-03 negativo: la capacidad es obligatoria")
    void estadio_capacidadObligatoria() {
        assertThat(camposInvalidos(new EstadioRequest("Pascual Guerrero", "Cali", null))).containsExactly("capacidad");
    }

    @ParameterizedTest(name = "ciudad \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("EST-03 negativo: la ciudad es obligatoria (vacía o solo espacios)")
    void estadio_ciudadVacia(String ciudad) {
        EstadioRequest request = new EstadioRequest("Pascual Guerrero", ciudad, 100);

        assertThat(camposInvalidos(request)).containsExactly("ciudad");
        assertThat(mensajeDe(request, "ciudad")).isEqualTo("La ciudad es obligatoria");
    }

    @Test
    @DisplayName("EST-03 negativo: la ciudad ausente (null) es inválida")
    void estadio_ciudadNula() {
        assertThat(camposInvalidos(new EstadioRequest("Pascual Guerrero", null, 100))).containsExactly("ciudad");
    }

    @Test
    @DisplayName("Estadio: el nombre es obligatorio y no puede superar los 100 caracteres de la columna")
    void estadio_nombre() {
        assertThat(camposInvalidos(new EstadioRequest(" ", "Cali", 100))).containsExactly("nombre");
        assertThat(camposInvalidos(new EstadioRequest(null, "Cali", 100))).containsExactly("nombre");
        assertThat(camposInvalidos(new EstadioRequest("x".repeat(100), "Cali", 100))).isEmpty();
        assertThat(camposInvalidos(new EstadioRequest("x".repeat(101), "Cali", 100))).containsExactly("nombre");
        assertThat(camposInvalidos(new EstadioRequest("Estadio", "y".repeat(101), 100))).containsExactly("ciudad");
    }

    // ------------------------------------------------------------------ Arbitro

    @Test
    @DisplayName("Árbitro positivo: 0 años de experiencia (el mínimo) y nacionalidad opcional son válidos")
    void arbitro_valido() {
        assertThat(camposInvalidos(new ArbitroRequest("Wilmar Roldán", "Colombia", 0))).isEmpty();
        assertThat(camposInvalidos(new ArbitroRequest("Wilmar Roldán", null, 20))).isEmpty();
    }

    @Test
    @DisplayName("Árbitro negativo: años de experiencia negativos o ausentes son inválidos")
    void arbitro_aniosInvalidos() {
        ArbitroRequest negativo = new ArbitroRequest("Wilmar Roldán", "Colombia", -1);

        assertThat(camposInvalidos(negativo)).containsExactly("aniosExperiencia");
        assertThat(mensajeDe(negativo, "aniosExperiencia")).isEqualTo("Los años de experiencia no pueden ser negativos");
        assertThat(camposInvalidos(new ArbitroRequest("Wilmar Roldán", "Colombia", null)))
                .containsExactly("aniosExperiencia");
    }

    @Test
    @DisplayName("Árbitro negativo: el nombre es obligatorio y la nacionalidad no puede superar los 60 caracteres")
    void arbitro_textos() {
        assertThat(camposInvalidos(new ArbitroRequest("", "Colombia", 1))).containsExactly("nombre");
        assertThat(camposInvalidos(new ArbitroRequest("Wilmar", "x".repeat(60), 1))).isEmpty();
        assertThat(camposInvalidos(new ArbitroRequest("Wilmar", "x".repeat(61), 1))).containsExactly("nacionalidad");
    }

    // --------------------------------------------------------------- Entrenador

    @Test
    @DisplayName("Entrenador positivo: nombre con nacionalidad opcional es válido")
    void entrenador_valido() {
        assertThat(camposInvalidos(new EntrenadorRequest("Alexandre Guimarães", "Brasil"))).isEmpty();
        assertThat(camposInvalidos(new EntrenadorRequest("Alexandre Guimarães", null))).isEmpty();
    }

    @ParameterizedTest(name = "nombre \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("Entrenador negativo: el nombre es obligatorio")
    void entrenador_nombreObligatorio(String nombre) {
        EntrenadorRequest request = new EntrenadorRequest(nombre, "Brasil");

        assertThat(camposInvalidos(request)).containsExactly("nombre");
        assertThat(mensajeDe(request, "nombre")).isEqualTo("El nombre es obligatorio");
        assertThat(camposInvalidos(new EntrenadorRequest(null, "Brasil"))).containsExactly("nombre");
    }

    @Test
    @DisplayName("Entrenador negativo: longitudes por encima de las columnas son inválidas")
    void entrenador_longitudes() {
        assertThat(camposInvalidos(new EntrenadorRequest("x".repeat(101), "Brasil"))).containsExactly("nombre");
        assertThat(camposInvalidos(new EntrenadorRequest("Nombre", "x".repeat(61)))).containsExactly("nacionalidad");
    }

    // --------------------------------------------------------------------- Liga

    @Test
    @DisplayName("Liga positivo: con nombre, temporada y ambas fechas es válida")
    void liga_valida() {
        assertThat(camposInvalidos(new LigaRequest("Liga BetPlay", "2026-I",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)))).isEmpty();
    }

    @Test
    @DisplayName("Liga negativo: nombre y temporada obligatorios; fechas obligatorias")
    void liga_camposObligatorios() {
        assertThat(camposInvalidos(new LigaRequest("", " ", null, null)))
                .containsExactlyInAnyOrder("nombre", "temporada", "fechaInicio", "fechaFin");
    }

    @Test
    @DisplayName("Liga negativo: la temporada no puede superar los 20 caracteres de la columna")
    void liga_longitudes() {
        LocalDate inicio = LocalDate.of(2026, 2, 1);
        LocalDate fin = LocalDate.of(2026, 6, 30);

        assertThat(camposInvalidos(new LigaRequest("Liga", "x".repeat(20), inicio, fin))).isEmpty();
        assertThat(camposInvalidos(new LigaRequest("Liga", "x".repeat(21), inicio, fin))).containsExactly("temporada");
        assertThat(camposInvalidos(new LigaRequest("x".repeat(101), "2026-I", inicio, fin))).containsExactly("nombre");
    }

    @Test
    @DisplayName("Liga: Bean Validation no compara las fechas entre sí (eso es la regla 422 LIGA_FECHAS_INVALIDAS del servicio)")
    void liga_fechasInvertidasLasValidaElServicio() {
        assertThat(camposInvalidos(new LigaRequest("Liga", "2026-I",
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 2, 1)))).isEmpty();
    }
}

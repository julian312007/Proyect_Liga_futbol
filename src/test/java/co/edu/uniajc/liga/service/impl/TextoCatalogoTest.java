package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba de la normalización de textos de los catálogos. Sin Spring ni Docker. */
class TextoCatalogoTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "Estadio Pascual Guerrero|Estadio Pascual Guerrero",
            "  Estadio Pascual Guerrero  |Estadio Pascual Guerrero",
            "Estadio    Pascual     Guerrero|Estadio Pascual Guerrero",
            "\tEstadio \t Pascual Guerrero\t|Estadio Pascual Guerrero"
    })
    @DisplayName("normalizar quita los espacios de los extremos y reduce los repetidos a uno")
    void normalizar_quitaEspaciosSobrantes(String original, String esperado) {
        assertThat(TextoCatalogo.normalizar(original)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("normalizar trata el espacio no separable (U+00A0) como un espacio más")
    void normalizar_espacioNoSeparable() {
        assertThat(TextoCatalogo.normalizar(" Atanasio  Girardot ")).isEqualTo("Atanasio Girardot");
    }

    @Test
    @DisplayName("normalizar conserva las mayúsculas y los acentos tal como llegan")
    void normalizar_conservaMayusculasYAcentos() {
        assertThat(TextoCatalogo.normalizar("  Estadio  Metropolitano ROBERTO Meléndez ")).isEqualTo("Estadio Metropolitano ROBERTO Meléndez");
    }

    @Test
    @DisplayName("normalizar(null) devuelve null")
    void normalizar_nulo() {
        assertThat(TextoCatalogo.normalizar(null)).isNull();
    }

    @Test
    @DisplayName("normalizarOpcional: un texto vacío o solo con espacios queda en null")
    void normalizarOpcional_vacioEsNulo() {
        assertThat(TextoCatalogo.normalizarOpcional("   ")).isNull();
        assertThat(TextoCatalogo.normalizarOpcional("")).isNull();
        assertThat(TextoCatalogo.normalizarOpcional(null)).isNull();
    }

    @Test
    @DisplayName("normalizarOpcional: un texto con contenido se normaliza")
    void normalizarOpcional_conContenido() {
        assertThat(TextoCatalogo.normalizarOpcional("  Colombia ")).isEqualTo("Colombia");
    }

    @Test
    @DisplayName("normalizarObligatorio: devuelve el texto normalizado")
    void normalizarObligatorio_conContenido() {
        assertThat(TextoCatalogo.normalizarObligatorio("  Liga   BetPlay ", "nombre")).isEqualTo("Liga BetPlay");
    }

    @Test
    @DisplayName("normalizarObligatorio: si queda vacío (solo espacios no separables) lanza 422 CAMPO_OBLIGATORIO")
    void normalizarObligatorio_vacioLanzaReglaDeNegocio() {
        assertThatThrownBy(() -> TextoCatalogo.normalizarObligatorio("  ", "nombre"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo(TextoCatalogo.CODIGO_CAMPO_VACIO);
                    assertThat(ex.getMessage()).contains("nombre");
                });
    }

    @Test
    @DisplayName("normalizarObligatorio: null también se rechaza")
    void normalizarObligatorio_nuloLanzaReglaDeNegocio() {
        assertThatThrownBy(() -> TextoCatalogo.normalizarObligatorio(null, "ciudad"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}

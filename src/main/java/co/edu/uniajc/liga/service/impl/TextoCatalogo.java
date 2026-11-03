package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.exception.ReglaNegocioException;

import java.util.regex.Pattern;

/**
 * Normalización de los textos de entrada de los catálogos (Fase 2): sin espacios al inicio ni al final y con
 * cualquier secuencia de espacios (incluidos los no separables y los tabuladores) reducida a un solo espacio.
 * Se aplica antes de comparar (EST-01, unicidad de la liga) y antes de guardar, para que la base de datos
 * reciba exactamente lo que se comparó.
 */
final class TextoCatalogo {

    /** Código del error cuando un texto obligatorio solo tenía espacios que Bean Validation no reconoce. */
    static final String CODIGO_CAMPO_VACIO = "CAMPO_OBLIGATORIO";

    /** {@code UNICODE_CHARACTER_CLASS} hace que {@code \s} incluya también el espacio no separable. */
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private TextoCatalogo() {
    }

    /** Texto normalizado; {@code null} si el original es {@code null}. */
    static String normalizar(String texto) {
        return texto == null ? null : ESPACIOS.matcher(texto).replaceAll(" ").strip();
    }

    /** Texto normalizado, o {@code null} si queda vacío (para campos opcionales como la nacionalidad). */
    static String normalizarOpcional(String texto) {
        String normalizado = normalizar(texto);
        return normalizado == null || normalizado.isEmpty() ? null : normalizado;
    }

    /**
     * Texto normalizado de un campo obligatorio. {@code @NotBlank} ya rechaza (400) los textos vacíos, pero
     * no reconoce como espacio el espacio no separable; en ese caso extraño el campo quedaría vacío al
     * normalizar, así que aquí se rechaza con 422.
     */
    static String normalizarObligatorio(String texto, String campo) {
        String normalizado = normalizar(texto);
        if (normalizado == null || normalizado.isEmpty()) {
            throw new ReglaNegocioException(CODIGO_CAMPO_VACIO, "El campo '%s' es obligatorio.".formatted(campo));
        }
        return normalizado;
    }
}

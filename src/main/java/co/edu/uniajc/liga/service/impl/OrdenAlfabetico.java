package co.edu.uniajc.liga.service.impl;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Orden alfabético en español para los nombres que desempatan las tablas de estadísticas. Con
 * {@code String.CASE_INSENSITIVE_ORDER} un nombre con tilde ("Águilas") quedaría después de la Z, porque compara
 * por código de carácter; el {@link Collator} los ordena como una persona ("Águilas" antes que "Búhos").
 */
final class OrdenAlfabetico {

    private static final Collator COLLATOR = Collator.getInstance(Locale.forLanguageTag("es-CO"));

    /** Compara dos nombres sin que las tildes ni las mayúsculas cambien su lugar en el abecedario. */
    static final Comparator<String> POR_NOMBRE = (a, b) -> COLLATOR.compare(a, b);

    private OrdenAlfabetico() {
    }
}

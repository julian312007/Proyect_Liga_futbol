package co.edu.uniajc.liga.dto.response;

/**
 * Resumen {@code {id, nombre}} de una entidad relacionada. Los {@code XResponse} lo usan en lugar de
 * anidar la entidad completa: evita ciclos y respuestas enormes.
 */
public record ResumenResponse(Long id, String nombre) {
}

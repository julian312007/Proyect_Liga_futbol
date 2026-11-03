package co.edu.uniajc.liga.dto.response;

/**
 * Entrenador tal como lo ve el cliente.
 *
 * @param id           identificador
 * @param nombre       nombre completo
 * @param nacionalidad nacionalidad; puede ser nula
 */
public record EntrenadorResponse(Long id, String nombre, String nacionalidad) {
}

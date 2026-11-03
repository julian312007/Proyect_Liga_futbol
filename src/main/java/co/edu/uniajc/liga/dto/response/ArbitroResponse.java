package co.edu.uniajc.liga.dto.response;

/**
 * Árbitro tal como lo ve el cliente.
 *
 * @param id               identificador
 * @param nombre           nombre completo
 * @param nacionalidad     nacionalidad; puede ser nula
 * @param aniosExperiencia años de experiencia
 */
public record ArbitroResponse(Long id, String nombre, String nacionalidad, Integer aniosExperiencia) {
}

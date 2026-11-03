package co.edu.uniajc.liga.dto.response;

/**
 * Estadio tal como lo ve el cliente. El nombre y la ciudad salen normalizados (sin espacios sobrantes).
 *
 * @param id        identificador
 * @param nombre    nombre del estadio
 * @param ciudad    ciudad donde está
 * @param capacidad aforo máximo
 */
public record EstadioResponse(Long id, String nombre, String ciudad, Integer capacidad) {
}

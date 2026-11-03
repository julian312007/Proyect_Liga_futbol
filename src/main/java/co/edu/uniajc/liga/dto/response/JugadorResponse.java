package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.PosicionJugador;

import java.time.LocalDate;

/**
 * Jugador tal como lo ve el cliente. El equipo se muestra como resumen {@code {id, nombre}}.
 *
 * @param id              identificador del jugador
 * @param nombre          nombre del jugador
 * @param fechaNacimiento fecha de nacimiento
 * @param posicion        posición en la que juega
 * @param dorsal          número de camiseta (único dentro del equipo)
 * @param equipo          equipo al que pertenece
 */
public record JugadorResponse(Long id, String nombre, LocalDate fechaNacimiento, PosicionJugador posicion,
                              Integer dorsal, ResumenResponse equipo) {
}

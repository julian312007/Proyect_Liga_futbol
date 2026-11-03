package co.edu.uniajc.liga.dto.response;

import java.time.LocalDate;

/**
 * Liga tal como la ve el cliente.
 *
 * @param id          identificador
 * @param nombre      nombre de la liga
 * @param temporada   temporada o torneo
 * @param fechaInicio primer día de la liga
 * @param fechaFin    último día de la liga
 */
public record LigaResponse(Long id, String nombre, String temporada, LocalDate fechaInicio, LocalDate fechaFin) {
}

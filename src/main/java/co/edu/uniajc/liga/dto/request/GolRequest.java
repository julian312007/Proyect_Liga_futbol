package co.edu.uniajc.liga.dto.request;

import co.edu.uniajc.liga.entity.TipoGol;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos para registrar un gol en un partido ({@code POST /api/v1/partidos/{partidoId}/goles}).
 *
 * <p>El partido viene en la ruta. El cliente no envía el equipo: se deduce del jugador. En un
 * {@link TipoGol#AUTOGOL} el jugador es quien marca en contra y el gol suma al equipo contrario.
 *
 * @param jugadorId id del jugador que marca (debe ser de uno de los dos equipos del partido, RN-08)
 * @param minuto    minuto del gol, entre 1 y 130 (incluye el tiempo añadido)
 * @param tipo      {@code NORMAL}, {@code PENAL} o {@code AUTOGOL}
 */
public record GolRequest(

        @NotNull(message = "El jugador es obligatorio")
        @Positive(message = "El id del jugador debe ser positivo")
        Long jugadorId,

        @NotNull(message = "El minuto es obligatorio")
        @Min(value = 1, message = "El minuto debe estar entre 1 y 130")
        @Max(value = 130, message = "El minuto debe estar entre 1 y 130")
        Integer minuto,

        @NotNull(message = "El tipo de gol es obligatorio")
        TipoGol tipo) {
}

package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Marcador con el que se cierra un partido ({@code POST /api/v1/partidos/{partidoId}/finalizar}).
 *
 * <p>Debe coincidir con los goles registrados (RN-11): los goles NORMAL y PENAL de cada equipo más los
 * autogoles del rival. Si no coincide, el partido no se finaliza.
 *
 * @param golesLocal     goles del equipo local, cero o más
 * @param golesVisitante goles del equipo visitante, cero o más
 */
public record FinalizarPartidoRequest(

        @NotNull(message = "Los goles del local son obligatorios")
        @PositiveOrZero(message = "Los goles del local no pueden ser negativos")
        Integer golesLocal,

        @NotNull(message = "Los goles del visitante son obligatorios")
        @PositiveOrZero(message = "Los goles del visitante no pueden ser negativos")
        Integer golesVisitante) {
}

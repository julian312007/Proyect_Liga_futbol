package co.edu.uniajc.liga.dto.request;

import co.edu.uniajc.liga.entity.TipoTarjeta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos para registrar una tarjeta en un partido ({@code POST /api/v1/partidos/{partidoId}/tarjetas}).
 *
 * <p>La segunda amarilla a un jugador en el partido genera, además, una roja en el mismo minuto (RN-09):
 * el cliente no debe enviar esa roja.
 *
 * @param jugadorId id del jugador sancionado (debe ser de uno de los dos equipos del partido, RN-08)
 * @param tipo      {@code AMARILLA} o {@code ROJA}
 * @param minuto    minuto de la tarjeta, entre 1 y 130 (incluye el tiempo añadido)
 */
public record TarjetaRequest(

        @NotNull(message = "El jugador es obligatorio")
        @Positive(message = "El id del jugador debe ser positivo")
        Long jugadorId,

        @NotNull(message = "El tipo de tarjeta es obligatorio")
        TipoTarjeta tipo,

        @NotNull(message = "El minuto es obligatorio")
        @Min(value = 1, message = "El minuto debe estar entre 1 y 130")
        @Max(value = 130, message = "El minuto debe estar entre 1 y 130")
        Integer minuto) {
}

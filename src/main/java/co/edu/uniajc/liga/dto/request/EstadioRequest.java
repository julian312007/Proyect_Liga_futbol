package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o reemplazar un estadio ({@code POST} y {@code PUT /api/v1/estadios}).
 *
 * <p>EST-03: la {@code ciudad} es obligatoria y la {@code capacidad} debe ser mayor que cero; si no, la
 * respuesta es 400. No lleva {@code id}: lo asigna la base de datos. El nombre y la ciudad se normalizan
 * (espacios sobrantes) en {@code EstadioServiceImpl}, que además aplica EST-01.
 *
 * @param nombre    nombre del estadio (hasta 100 caracteres)
 * @param ciudad    ciudad donde está el estadio (hasta 100 caracteres)
 * @param capacidad aforo máximo; mayor que cero
 */
public record EstadioRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @NotNull(message = "La capacidad es obligatoria")
        @Positive(message = "La capacidad debe ser mayor que cero")
        Integer capacidad) {
}

package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o reemplazar un árbitro ({@code POST} y {@code PUT /api/v1/arbitros}).
 *
 * @param nombre           nombre completo (obligatorio, hasta 100 caracteres)
 * @param nacionalidad     nacionalidad (opcional, hasta 60 caracteres)
 * @param aniosExperiencia años de experiencia; cero o más
 */
public record ArbitroRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 60, message = "La nacionalidad no puede superar los 60 caracteres")
        String nacionalidad,

        @NotNull(message = "Los años de experiencia son obligatorios")
        @PositiveOrZero(message = "Los años de experiencia no pueden ser negativos")
        Integer aniosExperiencia) {
}

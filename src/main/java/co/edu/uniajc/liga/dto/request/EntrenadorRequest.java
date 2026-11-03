package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o reemplazar un entrenador ({@code POST} y {@code PUT /api/v1/entrenadores}).
 *
 * @param nombre       nombre completo (obligatorio, hasta 100 caracteres)
 * @param nacionalidad nacionalidad (opcional, hasta 60 caracteres)
 */
public record EntrenadorRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 60, message = "La nacionalidad no puede superar los 60 caracteres")
        String nacionalidad) {
}

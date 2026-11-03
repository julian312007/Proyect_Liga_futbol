package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos para crear o reemplazar una liga ({@code POST} y {@code PUT /api/v1/ligas}).
 *
 * <p>Que {@code fechaFin} sea posterior a {@code fechaInicio} (422, {@code LIGA_FECHAS_INVALIDAS}) y que no
 * exista otra liga con el mismo nombre y temporada (409, {@code LIGA_DUPLICADA}) son reglas de negocio que
 * aplica {@code LigaServiceImpl}, no Bean Validation.
 *
 * @param nombre      nombre de la liga (obligatorio, hasta 100 caracteres)
 * @param temporada   temporada o torneo, por ejemplo {@code 2026-I} (obligatoria, hasta 20 caracteres)
 * @param fechaInicio primer día de la liga (formato {@code AAAA-MM-DD})
 * @param fechaFin    último día de la liga; posterior a {@code fechaInicio}
 */
public record LigaRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "La temporada es obligatoria")
        @Size(max = 20, message = "La temporada no puede superar los 20 caracteres")
        String temporada,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate fechaFin) {
}

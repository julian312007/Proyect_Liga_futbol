package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o reemplazar un equipo ({@code POST} y {@code PUT /api/v1/equipos}). Las relaciones llegan
 * por id; el cliente nunca fija el {@code id} del equipo.
 *
 * <p>Que el año de fundación no sea futuro y que el nombre y el entrenador no se repitan son reglas de negocio
 * que valida {@code EquipoServiceImpl} (422 y 409), no Bean Validation.
 *
 * @param nombre         nombre del equipo; único dentro de su liga
 * @param ciudad         ciudad del equipo
 * @param anioFundacion  año de fundación (mayor que cero y no posterior al año actual)
 * @param estadioLocalId id del estadio local (un estadio puede ser local de varios equipos)
 * @param entrenadorId   id del entrenador (un entrenador dirige a un solo equipo, RN-03)
 * @param ligaId         id de la liga en la que juega
 */
public record EquipoRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @NotNull(message = "El año de fundación es obligatorio")
        @Positive(message = "El año de fundación debe ser mayor que cero")
        Integer anioFundacion,

        @NotNull(message = "El estadio local es obligatorio")
        @Positive(message = "El id del estadio local debe ser mayor que cero")
        Long estadioLocalId,

        @NotNull(message = "El entrenador es obligatorio")
        @Positive(message = "El id del entrenador debe ser mayor que cero")
        Long entrenadorId,

        @NotNull(message = "La liga es obligatoria")
        @Positive(message = "El id de la liga debe ser mayor que cero")
        Long ligaId) {
}

package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Datos para programar o reprogramar un partido. Las relaciones llegan por id.
 *
 * <p>No incluye el id, el marcador ni el estado: el cliente no los fija. Un partido nace {@code PROGRAMADO}
 * con marcador 0-0, el estado cambia solo con las acciones del ciclo de vida ({@code iniciar}, {@code finalizar})
 * y el marcador lo valida el cierre del partido (RN-11).
 *
 * @param ligaId            liga a la que pertenece el partido
 * @param jornada           número de jornada, 1 o mayor (la jornada es un entero, no una entidad)
 * @param fechaHora         fecha y hora del partido con zona (ISO-8601, por ejemplo {@code 2026-02-01T20:00:00-05:00})
 * @param equipoLocalId     equipo local
 * @param equipoVisitanteId equipo visitante; debe ser distinto del local (RN-04)
 * @param estadioId         estadio donde se juega
 * @param arbitroId         árbitro que dirige el partido
 */
public record PartidoRequest(
        @NotNull(message = "La liga es obligatoria.")
        Long ligaId,

        @NotNull(message = "La jornada es obligatoria.")
        @Min(value = 1, message = "La jornada debe ser 1 o mayor.")
        Integer jornada,

        @NotNull(message = "La fecha y hora del partido es obligatoria.")
        Instant fechaHora,

        @NotNull(message = "El equipo local es obligatorio.")
        Long equipoLocalId,

        @NotNull(message = "El equipo visitante es obligatorio.")
        Long equipoVisitanteId,

        @NotNull(message = "El estadio es obligatorio.")
        Long estadioId,

        @NotNull(message = "El árbitro es obligatorio.")
        Long arbitroId) {
}

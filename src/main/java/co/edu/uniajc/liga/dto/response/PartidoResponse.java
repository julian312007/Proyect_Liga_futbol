package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.EstadoPartido;

import java.time.Instant;

/**
 * Partido tal como lo ve el cliente. Las relaciones se resumen como {@code {id, nombre}} para evitar ciclos y
 * respuestas enormes.
 *
 * @param fechaHora instante del partido; se serializa en ISO-8601 en UTC (por ejemplo {@code 2026-02-02T01:00:00Z})
 */
public record PartidoResponse(
        Long id,
        ResumenResponse liga,
        Integer jornada,
        Instant fechaHora,
        ResumenResponse equipoLocal,
        ResumenResponse equipoVisitante,
        ResumenResponse estadio,
        ResumenResponse arbitro,
        Integer golesLocal,
        Integer golesVisitante,
        EstadoPartido estado) {
}

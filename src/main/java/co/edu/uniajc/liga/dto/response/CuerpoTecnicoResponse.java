package co.edu.uniajc.liga.dto.response;

/**
 * Cuerpo técnico de un equipo ({@code GET /api/v1/equipos/{id}/cuerpo-tecnico}). Hoy el modelo solo tiene al
 * entrenador, que dirige a un solo equipo (RN-03); si en el futuro se agregan asistentes, este DTO crece sin
 * romper la ruta.
 *
 * @param equipoId     id del equipo
 * @param equipo       nombre del equipo
 * @param entrenadorId id del entrenador
 * @param entrenador   nombre del entrenador
 * @param nacionalidad nacionalidad del entrenador; {@code null} si no se registró
 */
public record CuerpoTecnicoResponse(Long equipoId, String equipo, Long entrenadorId, String entrenador,
                                    String nacionalidad) {
}

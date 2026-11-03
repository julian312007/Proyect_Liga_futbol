package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.EstadoPartido;

import java.time.Instant;

/**
 * Un partido del historial de un equipo (F6), visto desde ese equipo.
 *
 * @param id              id del partido
 * @param liga            liga a la que pertenece
 * @param jornada         jornada dentro de la liga
 * @param fechaHora       fecha y hora del partido (ISO-8601 con zona)
 * @param equipoLocal     equipo local
 * @param equipoVisitante equipo visitante
 * @param golesLocal      goles del local (0 mientras el partido no termina)
 * @param golesVisitante  goles del visitante (0 mientras el partido no termina)
 * @param estado          estado del partido
 * @param esLocal         {@code true} si el equipo consultado jugó de local
 * @param resultado       resultado para el equipo consultado; {@code null} si el partido no está
 *                        {@code FINALIZADO}
 */
public record PartidoHistorialResponse(Long id, ResumenResponse liga, Integer jornada, Instant fechaHora,
                                       ResumenResponse equipoLocal, ResumenResponse equipoVisitante,
                                       Integer golesLocal, Integer golesVisitante, EstadoPartido estado,
                                       boolean esLocal, Resultado resultado) {

    /** Resultado de un partido finalizado desde la perspectiva del equipo consultado. */
    public enum Resultado {
        VICTORIA,
        EMPATE,
        DERROTA
    }
}

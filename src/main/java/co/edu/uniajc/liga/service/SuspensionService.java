package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.response.SuspendidoResponse;

import java.util.List;

/**
 * Regla de suspensión por expulsión (RN-10), compartida entre fases: la implementa la Fase 5 (que rechaza los
 * eventos de un suspendido) y la consume la Fase 6 (que lista a los suspendidos de una jornada).
 *
 * <p><b>Definición única:</b> un jugador está suspendido en el partido P si recibió una tarjeta ROJA (directa
 * o por doble amarilla) en el partido inmediatamente anterior de su equipo, ordenado por {@code fechaHora}
 * dentro de la misma liga. Solo cuentan partidos anteriores a P; el "anterior" es el último de ese equipo con
 * {@code fechaHora} menor que la de P.
 */
public interface SuspensionService {

    /** {@code true} si el jugador no puede participar en el partido por una expulsión previa (RN-10). */
    boolean estaSuspendido(Long jugadorId, Long partidoId);

    /** Jugadores de los dos equipos del partido que están suspendidos para jugarlo. Lista vacía si ninguno. */
    List<SuspendidoResponse> suspendidosEnPartido(Long partidoId);
}

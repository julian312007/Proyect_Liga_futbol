package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;

/**
 * Cierre de un partido (F3; RN-11 y RN-12). El plan lo llama {@code PartidoService.finalizar}; vive en su
 * propia interfaz porque {@code PartidoService} pertenece a la Fase 4.
 */
public interface CierrePartidoService {

    /**
     * Finaliza el partido: solo desde {@code EN_JUEGO}, y solo si el marcador recibido coincide con los goles
     * registrados (RN-11). Guarda el marcador, pasa el estado a {@code FINALIZADO} y, como la tabla de
     * posiciones y los goleadores de la liga cambian, invalida esas dos cachés (clave = id de la liga).
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException (404) si el partido no existe
     * @throws co.edu.uniajc.liga.exception.ConflictoException (409) {@code RN-12} si ya estaba
     *         {@code FINALIZADO}; {@code PARTIDO_ESTADO_INVALIDO} si aún está {@code PROGRAMADO}
     * @throws co.edu.uniajc.liga.exception.ReglaNegocioException (422) {@code RN-11} si el marcador no coincide
     */
    ResultadoPartidoResponse finalizar(Long partidoId, FinalizarPartidoRequest request);
}

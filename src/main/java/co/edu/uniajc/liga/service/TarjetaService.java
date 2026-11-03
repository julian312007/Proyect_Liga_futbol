package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import org.springframework.data.domain.Pageable;

/**
 * Tarjetas de un partido (F3; RN-08, RN-09, RN-10 y RN-12).
 *
 * <p>Errores: {@code RecursoNoEncontradoException} (404) si el partido, el jugador o la tarjeta no existen;
 * {@code ConflictoException} (409) si el partido no está {@code EN_JUEGO} (código {@code RN-12} si ya está
 * {@code FINALIZADO}, {@code PARTIDO_NO_EN_JUEGO} si aún está {@code PROGRAMADO});
 * {@code ReglaNegocioException} (422) por RN-08, RN-10 o {@code TARJETA_JUGADOR_EXPULSADO}.
 */
public interface TarjetaService {

    /**
     * Registra una tarjeta. El partido debe estar {@code EN_JUEGO}; el jugador debe ser de uno de los dos
     * equipos (RN-08), no estar suspendido (RN-10) ni haber sido expulsado ya en este partido.
     *
     * <p>RN-09: si es la segunda amarilla del jugador en el partido, en la misma transacción se crea también
     * una {@code ROJA} en el mismo minuto. Devuelve la tarjeta pedida (la amarilla); la roja automática se ve
     * en el listado.
     */
    TarjetaResponse registrar(Long partidoId, TarjetaRequest request);

    /** Tarjetas del partido, paginadas (orden por defecto: minuto y luego id). 404 si el partido no existe. */
    PaginaResponse<TarjetaResponse> listar(Long partidoId, Pageable pageable);

    /**
     * Elimina una tarjeta del partido. El partido debe estar {@code EN_JUEGO} (RN-12). Si era la segunda
     * amarilla, la roja automática no se borra sola: se elimina por separado.
     */
    void eliminar(Long partidoId, Long tarjetaId);
}

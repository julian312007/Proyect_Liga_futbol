package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;

import java.util.List;

/** Tabla de posiciones de una liga (F4, RN-01). La respuesta se guarda en la caché de Redis (D-3). */
public interface TablaPosicionesService {

    /**
     * Calcula la tabla de posiciones con los partidos {@code FINALIZADO} de la liga.
     *
     * <p>RN-01: victoria 3 puntos, empate 1, derrota 0. Orden: puntos, diferencia de gol y goles a favor
     * (de mayor a menor); si aún empatan, por nombre del equipo.
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException si la liga no existe
     */
    List<PosicionEquipoResponse> obtenerTabla(Long ligaId);
}

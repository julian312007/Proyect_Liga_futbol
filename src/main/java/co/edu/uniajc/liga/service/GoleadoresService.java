package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;

import java.util.List;

/** Tabla de goleadores de una liga (F5). La respuesta se guarda en la caché de Redis (D-3). */
public interface GoleadoresService {

    /**
     * Cuenta los goles {@code NORMAL} y {@code PENAL} de los partidos {@code FINALIZADO} de la liga (el
     * {@code AUTOGOL} no suma) y los ordena de mayor a menor.
     *
     * @throws co.edu.uniajc.liga.exception.RecursoNoEncontradoException si la liga no existe
     */
    List<GoleadorResponse> obtenerGoleadores(Long ligaId);
}

package co.edu.uniajc.liga.dto.response;

/**
 * Equipo tal como lo ve el cliente. Las relaciones se muestran como resumen {@code {id, nombre}} para evitar
 * ciclos y respuestas enormes.
 *
 * @param id            identificador del equipo
 * @param nombre        nombre (único dentro de la liga)
 * @param ciudad        ciudad del equipo
 * @param anioFundacion año de fundación
 * @param estadioLocal  estadio local
 * @param entrenador    entrenador del equipo
 * @param liga          liga en la que juega
 */
public record EquipoResponse(Long id, String nombre, String ciudad, Integer anioFundacion,
                             ResumenResponse estadioLocal, ResumenResponse entrenador, ResumenResponse liga) {
}

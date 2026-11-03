package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Estadio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code estadio}. Reemplaza a la versión base del dominio común con las consultas de
 * EST-01 (nombre único por ciudad sin distinguir mayúsculas), que respalda el índice único
 * {@code ux_estadio_ciudad_nombre} de la migración {@code V3__estadio.sql}.
 *
 * <p>Los parámetros deben llegar ya normalizados (sin espacios sobrantes): la comparación solo ignora
 * mayúsculas y minúsculas.
 */
public interface EstadioRepository extends JpaRepository<Estadio, Long> {

    /** EST-01 al crear: ¿existe un estadio con ese nombre en esa ciudad? */
    boolean existsByCiudadIgnoreCaseAndNombreIgnoreCase(String ciudad, String nombre);

    /** EST-01 al actualizar: igual que la anterior pero excluyendo al propio estadio. */
    boolean existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot(String ciudad, String nombre, Long id);
}

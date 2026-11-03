package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Liga;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code liga}. Reemplaza a la versión base del dominio común con las consultas de la
 * unicidad (nombre, temporada), que respalda la restricción {@code uq_liga_nombre_temporada} de
 * {@code V6__liga.sql}. Igual que en la base de datos, la comparación distingue mayúsculas; los parámetros
 * deben llegar ya normalizados (sin espacios sobrantes).
 */
public interface LigaRepository extends JpaRepository<Liga, Long> {

    /** Al crear: ¿existe una liga con ese nombre y esa temporada? */
    boolean existsByNombreAndTemporada(String nombre, String temporada);

    /** Al actualizar: igual que la anterior pero excluyendo a la propia liga. */
    boolean existsByNombreAndTemporadaAndIdNot(String nombre, String temporada, Long id);
}

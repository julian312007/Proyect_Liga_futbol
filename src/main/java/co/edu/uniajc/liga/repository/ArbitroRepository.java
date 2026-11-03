package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Arbitro;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code arbitro}. Versión base del dominio común: la Fase 2 la reemplaza por una versión
 * con las consultas propias de su servicio (conservando el nombre y el tipo de entidad).
 */
public interface ArbitroRepository extends JpaRepository<Arbitro, Long> {
}

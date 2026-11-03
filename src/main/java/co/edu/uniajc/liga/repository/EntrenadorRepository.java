package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Entrenador;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code entrenador}. Versión base del dominio común: la Fase 2 la reemplaza por una
 * versión con las consultas propias de su servicio (conservando el nombre y el tipo de entidad).
 */
public interface EntrenadorRepository extends JpaRepository<Entrenador, Long> {
}

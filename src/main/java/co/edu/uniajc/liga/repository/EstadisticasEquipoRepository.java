package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consulta de los equipos de una liga para la tabla de posiciones (Fase 6), de modo que un equipo que aún no
 * jugó aparezca con ceros. Es propio de la Fase 6 para no tocar el {@code EquipoRepository} de la Fase 3.
 */
public interface EstadisticasEquipoRepository extends JpaRepository<Equipo, Long> {

    /** Equipos inscritos en la liga, por nombre. Solo se leen id y nombre: las relaciones quedan sin cargar. */
    @Query("select e from Equipo e where e.liga.id = :ligaId order by e.nombre, e.id")
    List<Equipo> buscarDeLiga(@Param("ligaId") Long ligaId);
}

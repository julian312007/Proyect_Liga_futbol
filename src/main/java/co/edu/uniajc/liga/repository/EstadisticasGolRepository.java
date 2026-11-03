package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas de goles para la tabla de goleadores (Fase 6). Es propio de la Fase 6 para no tocar el
 * {@code GolRepository} de la Fase 5.
 */
public interface EstadisticasGolRepository extends JpaRepository<Gol, Long> {

    /**
     * Goles de los partidos de una liga que están en el {@code estado} indicado, con el jugador y su equipo
     * ya cargados (el servicio los agrupa por jugador sin más consultas).
     */
    @Query("""
            select g from Gol g
            join g.partido p
            join fetch g.jugador j
            join fetch j.equipo
            where p.liga.id = :ligaId and p.estado = :estado
            """)
    List<Gol> buscarDeLigaPorEstadoDelPartido(@Param("ligaId") Long ligaId, @Param("estado") EstadoPartido estado);
}

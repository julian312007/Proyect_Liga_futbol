package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Partido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas de partidos para las estadísticas (Fase 6): tabla de posiciones, suspendidos por jornada e
 * historial de un equipo. Es propio de la Fase 6 para no tocar el {@code PartidoRepository} de la Fase 4.
 *
 * <p>Todas las consultas traen las relaciones que el servicio necesita con {@code join fetch}, de modo que
 * recorrerlas después no dispara una consulta por fila (N+1).
 */
public interface EstadisticasPartidoRepository extends JpaRepository<Partido, Long> {

    /** Partidos de una liga en el {@code estado} indicado, con los dos equipos ya cargados. */
    @Query("""
            select p from Partido p
            join fetch p.equipoLocal
            join fetch p.equipoVisitante
            where p.liga.id = :ligaId and p.estado = :estado
            """)
    List<Partido> buscarDeLigaPorEstado(@Param("ligaId") Long ligaId, @Param("estado") EstadoPartido estado);

    /** Ids de los partidos de una jornada de la liga, en orden cronológico. */
    @Query("""
            select p.id from Partido p
            where p.liga.id = :ligaId and p.jornada = :jornada
            order by p.fechaHora, p.id
            """)
    List<Long> buscarIdsDeJornada(@Param("ligaId") Long ligaId, @Param("jornada") Integer jornada);

    /** Partidos en los que el equipo jugó de local o de visitante, con liga y equipos ya cargados. */
    @Query(value = """
            select p from Partido p
            join fetch p.liga
            join fetch p.equipoLocal
            join fetch p.equipoVisitante
            where p.equipoLocal.id = :equipoId or p.equipoVisitante.id = :equipoId
            """,
            countQuery = """
                    select count(p) from Partido p
                    where p.equipoLocal.id = :equipoId or p.equipoVisitante.id = :equipoId
                    """)
    Page<Partido> buscarHistorialDeEquipo(@Param("equipoId") Long equipoId, Pageable pageable);
}

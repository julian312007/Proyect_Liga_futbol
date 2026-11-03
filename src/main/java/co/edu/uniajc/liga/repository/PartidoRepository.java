package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Partido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code partido}. Reemplaza a la versión base del dominio común (conserva su nombre y su
 * tipo de entidad, así que las demás fases siguen usando {@code findById}, {@code save}, etc.).
 *
 * <p>Las consultas de choques de la Fase 4 reciben {@code excluirId} para que, al editar un partido, no choque
 * consigo mismo; para crear se pasa {@link #SIN_EXCLUSION} (ningún partido tiene ese id). Se usa un valor y no
 * {@code null} para no depender de cómo infiere Hibernate el tipo de un parámetro nulo.
 *
 * <p>Los partidos se cargan con sus cinco relaciones en una sola consulta ({@link EntityGraph}) porque todo
 * {@code PartidoResponse} las necesita: sin esto, un listado de 20 partidos haría decenas de consultas (N+1).
 */
public interface PartidoRepository extends JpaRepository<Partido, Long>, JpaSpecificationExecutor<Partido> {

    /** Id que no corresponde a ningún partido (los ids son positivos): "no excluir ninguno". */
    long SIN_EXCLUSION = -1L;

    // -------------------------------------------------------------------------- Lecturas con relaciones

    @Override
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "estadio", "arbitro"})
    Optional<Partido> findById(Long id);

    /** Calendario: la especificación aplica los filtros opcionales (ver {@code PartidoEspecificaciones}). */
    @Override
    @EntityGraph(attributePaths = {"liga", "equipoLocal", "equipoVisitante", "estadio", "arbitro"})
    Page<Partido> findAll(Specification<Partido> spec, Pageable pageable);

    // ------------------------------------------------------------------------------- Choques (RN-05 a RN-07)

    /**
     * RN-05: cuántos partidos, distintos de {@code excluirId}, tiene el equipo (como local o visitante) en esa
     * jornada de esa liga.
     */
    @Query("""
            select count(p) from Partido p
            where p.liga.id = :ligaId
              and p.jornada = :jornada
              and p.id <> :excluirId
              and (p.equipoLocal.id = :equipoId or p.equipoVisitante.id = :equipoId)
            """)
    long contarPartidosDelEquipoEnJornada(@Param("ligaId") Long ligaId,
                                          @Param("jornada") Integer jornada,
                                          @Param("equipoId") Long equipoId,
                                          @Param("excluirId") Long excluirId);

    /**
     * RN-06: fechas y horas de los otros partidos del estadio dentro de {@code [desde, hasta]} (ambos incluidos).
     * Es un prefiltro: quien decide si hay choque es el servicio, con su ventana de solape.
     */
    @Query("""
            select p.fechaHora from Partido p
            where p.estadio.id = :estadioId
              and p.id <> :excluirId
              and p.fechaHora between :desde and :hasta
            """)
    List<Instant> horariosOcupadosDelEstadio(@Param("estadioId") Long estadioId,
                                             @Param("excluirId") Long excluirId,
                                             @Param("desde") Instant desde,
                                             @Param("hasta") Instant hasta);

    /** RN-07: igual que {@link #horariosOcupadosDelEstadio} pero para los partidos del árbitro. */
    @Query("""
            select p.fechaHora from Partido p
            where p.arbitro.id = :arbitroId
              and p.id <> :excluirId
              and p.fechaHora between :desde and :hasta
            """)
    List<Instant> horariosOcupadosDelArbitro(@Param("arbitroId") Long arbitroId,
                                             @Param("excluirId") Long excluirId,
                                             @Param("desde") Instant desde,
                                             @Param("hasta") Instant hasta);

    // ---------------------------------------------------------- Dependientes (verificadores de borrado)

    boolean existsByEstadioId(Long estadioId);

    boolean existsByArbitroId(Long arbitroId);

    boolean existsByLigaId(Long ligaId);

    boolean existsByEquipoLocalIdOrEquipoVisitanteId(Long equipoLocalId, Long equipoVisitanteId);
}

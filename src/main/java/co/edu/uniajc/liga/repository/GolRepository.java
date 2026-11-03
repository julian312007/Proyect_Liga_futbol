package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Gol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code gol} (Fase 5). Reemplaza a la versión base del dominio común.
 */
public interface GolRepository extends JpaRepository<Gol, Long> {

    /** Goles de un partido, paginados. Carga el jugador y su equipo de una vez (evita N+1 al mapear). */
    @EntityGraph(attributePaths = {"jugador", "jugador.equipo"})
    Page<Gol> findByPartidoId(Long partidoId, Pageable pageable);

    /** Un gol de un partido concreto: un gol de otro partido no se puede borrar por esta ruta. */
    Optional<Gol> findByIdAndPartidoId(Long id, Long partidoId);

    /** Todos los goles de un partido con el jugador y su equipo cargados (RN-11 los suma por equipo). */
    @Query("select g from Gol g join fetch g.jugador j join fetch j.equipo where g.partido.id = :partidoId")
    List<Gol> findConEquipoByPartidoId(@Param("partidoId") Long partidoId);

    /** {@code true} si el jugador tiene al menos un gol (impide borrarlo, 409). */
    boolean existsByJugadorId(Long jugadorId);
}

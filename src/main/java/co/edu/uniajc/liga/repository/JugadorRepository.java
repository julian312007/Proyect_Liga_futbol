package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Jugador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a la tabla {@code jugador}. Reemplaza a la versión base del dominio común (Fase 3).
 *
 * <p>La relación con {@code Equipo} es {@code LAZY} y los {@code JugadorResponse} muestran el equipo como
 * resumen {@code {id, nombre}}; por eso las lecturas la traen con {@link EntityGraph} (una sola consulta).
 */
public interface JugadorRepository extends JpaRepository<Jugador, Long> {

    /** Listado paginado con el equipo ya cargado. */
    @Override
    @EntityGraph(attributePaths = "equipo")
    Page<Jugador> findAll(Pageable pageable);

    /** Un jugador con su equipo ya cargado. */
    @Override
    @EntityGraph(attributePaths = "equipo")
    Optional<Jugador> findById(Long id);

    /** Plantel de un equipo, paginado. */
    @EntityGraph(attributePaths = "equipo")
    Page<Jugador> findByEquipoId(Long equipoId, Pageable pageable);

    /** El equipo tiene al menos un jugador (impide borrarlo). */
    boolean existsByEquipoId(Long equipoId);

    /** Dorsal ya usado en el equipo: al crear. */
    boolean existsByEquipoIdAndDorsal(Long equipoId, Integer dorsal);

    /** Dorsal ya usado en el equipo: al actualizar, excluyendo al propio jugador. */
    boolean existsByEquipoIdAndDorsalAndIdNot(Long equipoId, Integer dorsal, Long id);
}

package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Equipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a la tabla {@code equipo}. Reemplaza a la versión base del dominio común (Fase 3).
 *
 * <p>Las relaciones de {@link Equipo} son {@code LAZY}; como {@code open-in-view} está desactivado y los
 * {@code XResponse} las muestran como resumen {@code {id, nombre}}, las consultas de lectura las traen con
 * {@link EntityGraph} para evitar el problema de las N+1 consultas. Ninguna de las tres es una colección, así
 * que paginar con ellas es seguro.
 */
public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    /** Listado paginado con liga, estadio local y entrenador ya cargados. */
    @Override
    @EntityGraph(attributePaths = {"liga", "estadioLocal", "entrenador"})
    Page<Equipo> findAll(Pageable pageable);

    /** Un equipo con liga, estadio local y entrenador ya cargados. */
    @Override
    @EntityGraph(attributePaths = {"liga", "estadioLocal", "entrenador"})
    Optional<Equipo> findById(Long id);

    /** Único (liga, nombre) sin distinguir mayúsculas: al crear. */
    boolean existsByLigaIdAndNombreIgnoreCase(Long ligaId, String nombre);

    /** Único (liga, nombre) sin distinguir mayúsculas: al actualizar, excluyendo el propio equipo. */
    boolean existsByLigaIdAndNombreIgnoreCaseAndIdNot(Long ligaId, String nombre, Long id);

    /** RN-03 al crear: el entrenador ya dirige a algún equipo. */
    boolean existsByEntrenadorId(Long entrenadorId);

    /** RN-03 al actualizar: el entrenador dirige a otro equipo distinto del que se edita. */
    boolean existsByEntrenadorIdAndIdNot(Long entrenadorId, Long id);

    /** EST-02: algún equipo tiene este estadio como local. */
    boolean existsByEstadioLocalId(Long estadioId);

    /** Algún equipo pertenece a esta liga. */
    boolean existsByLigaId(Long ligaId);
}

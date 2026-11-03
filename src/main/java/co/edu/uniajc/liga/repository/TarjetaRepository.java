package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code tarjeta} (Fase 5). Reemplaza a la versión base del dominio común.
 */
public interface TarjetaRepository extends JpaRepository<Tarjeta, Long> {

    /** Tarjetas de un partido, paginadas. Carga el jugador y su equipo de una vez (evita N+1 al mapear). */
    @EntityGraph(attributePaths = {"jugador", "jugador.equipo"})
    Page<Tarjeta> findByPartidoId(Long partidoId, Pageable pageable);

    /** Una tarjeta de un partido concreto: una tarjeta de otro partido no se puede borrar por esta ruta. */
    Optional<Tarjeta> findByIdAndPartidoId(Long id, Long partidoId);

    /** {@code true} si el jugador tiene al menos una tarjeta (impide borrarlo, 409). */
    boolean existsByJugadorId(Long jugadorId);

    /** {@code true} si el jugador recibió una tarjeta de ese tipo en el partido (por ejemplo, ya fue expulsado). */
    boolean existsByPartidoIdAndJugadorIdAndTipo(Long partidoId, Long jugadorId, TipoTarjeta tipo);

    /** Cuántas tarjetas de ese tipo recibió el jugador en el partido (RN-09 cuenta las amarillas). */
    long countByPartidoIdAndJugadorIdAndTipo(Long partidoId, Long jugadorId, TipoTarjeta tipo);

    /**
     * Tarjetas de un tipo recibidas en un partido por jugadores de un equipo, con el jugador cargado.
     * Con {@code tipo = ROJA} da los expulsados de ese equipo en ese partido (RN-10).
     */
    @Query("select t from Tarjeta t join fetch t.jugador j "
            + "where t.partido.id = :partidoId and t.tipo = :tipo and j.equipo.id = :equipoId "
            + "order by j.id")
    List<Tarjeta> findDeEquipoEnPartido(@Param("partidoId") Long partidoId,
                                        @Param("equipoId") Long equipoId,
                                        @Param("tipo") TipoTarjeta tipo);
}

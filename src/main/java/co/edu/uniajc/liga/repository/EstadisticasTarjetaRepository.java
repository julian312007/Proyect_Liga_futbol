package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Tarjeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas de tarjetas para las sanciones (Fase 6). Es propio de la Fase 6 para no tocar el
 * {@code TarjetaRepository} de la Fase 5.
 */
public interface EstadisticasTarjetaRepository extends JpaRepository<Tarjeta, Long> {

    /** Todas las tarjetas de los partidos de una liga, con el jugador y su equipo ya cargados. */
    @Query("""
            select t from Tarjeta t
            join t.partido p
            join fetch t.jugador j
            join fetch j.equipo
            where p.liga.id = :ligaId
            """)
    List<Tarjeta> buscarDeLiga(@Param("ligaId") Long ligaId);
}

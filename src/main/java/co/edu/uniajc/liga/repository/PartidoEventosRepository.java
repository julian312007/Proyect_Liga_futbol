package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Partido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas sobre {@link Partido} que necesitan los goles, las tarjetas, la suspensión y el cierre (Fase 5).
 * Es un repositorio propio de la Fase 5: no toca {@code PartidoRepository}, que es de la Fase 4.
 */
public interface PartidoEventosRepository extends JpaRepository<Partido, Long> {

    /**
     * Lee el partido con bloqueo de escritura ({@code SELECT ... FOR UPDATE}). Se usa en todo cambio de
     * goles, tarjetas o estado: serializa las operaciones sobre un mismo partido y así un gol no puede
     * entrar mientras el partido se está finalizando (RN-11 y RN-12). Solo vale dentro de una transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Partido p where p.id = :id")
    Optional<Partido> buscarBloqueadoPorId(@Param("id") Long id);

    /**
     * Partidos de un equipo en la liga con {@code fechaHora} anterior a {@code antesDe}, del más reciente
     * al más antiguo (el primero es "el partido anterior" de RN-10). El desempate por id hace el orden
     * determinista. Se limita con {@code PageRequest.of(0, 1)}.
     */
    @Query("select p from Partido p "
            + "where p.liga.id = :ligaId "
            + "and (p.equipoLocal.id = :equipoId or p.equipoVisitante.id = :equipoId) "
            + "and p.fechaHora < :antesDe "
            + "order by p.fechaHora desc, p.id desc")
    List<Partido> buscarAnterioresDeEquipo(@Param("ligaId") Long ligaId,
                                           @Param("equipoId") Long equipoId,
                                           @Param("antesDe") Instant antesDe,
                                           Pageable pageable);
}

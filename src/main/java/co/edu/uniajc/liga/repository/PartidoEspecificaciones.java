package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Partido;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros opcionales del calendario de partidos (F8). Se arman como especificación y no con una consulta de
 * parámetros nulos ({@code :x is null or ...}) porque solo se agrega la condición de los filtros presentes:
 * no hay parámetros nulos que Hibernate deba tipar.
 */
public final class PartidoEspecificaciones {

    private PartidoEspecificaciones() {
    }

    /**
     * Calendario filtrado. Cada filtro {@code null} se ignora; sin ninguno trae todos los partidos.
     *
     * @param equipoId partidos del equipo como local <em>o</em> como visitante
     */
    public static Specification<Partido> calendario(Long ligaId, Integer jornada, Long equipoId, Long estadioId) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (ligaId != null) {
                condiciones.add(cb.equal(root.get("liga").get("id"), ligaId));
            }
            if (jornada != null) {
                condiciones.add(cb.equal(root.get("jornada"), jornada));
            }
            if (equipoId != null) {
                condiciones.add(cb.or(
                        cb.equal(root.get("equipoLocal").get("id"), equipoId),
                        cb.equal(root.get("equipoVisitante").get("id"), equipoId)));
            }
            if (estadioId != null) {
                condiciones.add(cb.equal(root.get("estadio").get("id"), estadioId));
            }
            return condiciones.isEmpty() ? cb.conjunction() : cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}

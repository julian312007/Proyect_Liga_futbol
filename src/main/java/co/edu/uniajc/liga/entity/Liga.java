package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Liga o torneo de una temporada. Entidad independiente: se registra sin que exista otra tabla.
 * Corresponde a la tabla {@code liga} ({@code V6__liga.sql}); único por (nombre, temporada).
 *
 * <p>Solo {@code @Getter/@Setter}, nunca {@code @Data}. Con {@code ddl-auto=validate} las longitudes de
 * {@code @Column} deben coincidir exactamente con las de la migración.
 */
@Getter
@Setter
@Entity
@Table(name = "liga")
public class Liga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_liga")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "temporada", nullable = false, length = 20)
    private String temporada;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Siempre posterior a {@link #fechaInicio} (CHECK en la base de datos y validación en el servicio). */
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;
}

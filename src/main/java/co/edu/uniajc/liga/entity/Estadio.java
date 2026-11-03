package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Estadio. Entidad independiente (D-4). Tabla {@code estadio} ({@code V3__estadio.sql}).
 *
 * <p>EST-01: el nombre se guarda normalizado (sin espacios sobrantes) y es único por ciudad sin distinguir
 * mayúsculas (índice único sobre {@code lower(ciudad), lower(nombre)}).
 */
@Getter
@Setter
@Entity
@Table(name = "estadio")
public class Estadio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estadio")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    /** Siempre mayor que cero (EST-03; CHECK en la base de datos). */
    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;
}

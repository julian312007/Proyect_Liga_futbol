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
 * Entrenador. Entidad independiente. Tabla {@code entrenador} ({@code V5__entrenador.sql}).
 *
 * <p>Es el lado inverso de la relación 1—1 con {@link Equipo}: la clave foránea única vive en
 * {@code equipo.id_entrenador}, por eso aquí no hay referencia al equipo (RN-03).
 */
@Getter
@Setter
@Entity
@Table(name = "entrenador")
public class Entrenador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entrenador")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "nacionalidad", length = 60)
    private String nacionalidad;
}

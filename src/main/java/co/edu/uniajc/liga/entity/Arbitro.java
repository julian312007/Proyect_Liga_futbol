package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Árbitro. Entidad independiente. Tabla {@code arbitro} ({@code V4__arbitro.sql}). */
@Getter
@Setter
@Entity
@Table(name = "arbitro")
public class Arbitro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_arbitro")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "nacionalidad", length = 60)
    private String nacionalidad;

    /** Cero o más (CHECK en la base de datos). */
    @Column(name = "anios_experiencia", nullable = false)
    private Integer aniosExperiencia;
}

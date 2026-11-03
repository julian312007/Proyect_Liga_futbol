package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Equipo de una liga. Tabla {@code equipo} ({@code V7__equipo.sql}). Único por (liga, nombre).
 *
 * <p>Relaciones, todas {@code LAZY}: N—1 con {@link Liga}, N—1 con {@link Estadio} (estadio local; un
 * estadio puede ser local de varios equipos) y 1—1 con {@link Entrenador} (RN-03: la clave foránea
 * {@code id_entrenador} es única, así un entrenador dirige a un solo equipo).
 */
@Getter
@Setter
@Entity
@Table(name = "equipo")
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipo")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    /** Año de fundación; no puede ser futuro (lo valida el servicio). */
    @Column(name = "anio_fundacion", nullable = false)
    private Integer anioFundacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estadio_local", nullable = false)
    private Estadio estadioLocal;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entrenador", nullable = false, unique = true)
    private Entrenador entrenador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;
}

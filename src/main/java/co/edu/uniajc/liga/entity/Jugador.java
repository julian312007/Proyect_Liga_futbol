package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Jugador. Tabla {@code jugador} ({@code V8__jugador.sql}). Único por (equipo, dorsal).
 *
 * <p>RN-02: un jugador pertenece a un solo equipo a la vez; la relación N—1 con {@link Equipo} lo garantiza
 * y cambiar de equipo mueve al jugador, no lo duplica.
 */
@Getter
@Setter
@Entity
@Table(name = "jugador")
public class Jugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_jugador")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Siempre en el pasado (lo valida el servicio). */
    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "posicion", nullable = false, length = 20)
    private PosicionJugador posicion;

    /** Entre 1 y 99 (CHECK en la base de datos). */
    @Column(name = "dorsal", nullable = false)
    private Integer dorsal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_equipo", nullable = false)
    private Equipo equipo;
}

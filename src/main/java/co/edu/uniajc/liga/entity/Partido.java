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

import java.time.Instant;

/**
 * Partido de una liga. Tabla {@code partido} ({@code V9__partido.sql}).
 *
 * <p>La base de datos es la última defensa de varias reglas: CHECK de equipos distintos (RN-04) e índices
 * únicos {@code (id_estadio, fecha_hora)} (RN-06) y {@code (id_arbitro, fecha_hora)} (RN-07). El marcador
 * ({@code golesLocal}/{@code golesVisitante}) se guarda y se valida contra los goles al finalizar (RN-11).
 * Ciclo de vida: ver {@link EstadoPartido}.
 */
@Getter
@Setter
@Entity
@Table(name = "partido")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_partido")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_liga", nullable = false)
    private Liga liga;

    /** Uno o más (CHECK en la base de datos). La jornada es un entero, no una entidad. */
    @Column(name = "jornada", nullable = false)
    private Integer jornada;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_equipo_local", nullable = false)
    private Equipo equipoLocal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_equipo_visitante", nullable = false)
    private Equipo equipoVisitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estadio", nullable = false)
    private Estadio estadio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_arbitro", nullable = false)
    private Arbitro arbitro;

    @Column(name = "goles_local", nullable = false)
    private Integer golesLocal = 0;

    @Column(name = "goles_visitante", nullable = false)
    private Integer golesVisitante = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPartido estado = EstadoPartido.PROGRAMADO;
}

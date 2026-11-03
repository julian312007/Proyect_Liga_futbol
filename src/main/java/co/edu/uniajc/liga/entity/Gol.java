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

/**
 * Gol de un {@link Partido}. Tabla {@code gol} ({@code V10__gol.sql}). Solo se registra con el partido
 * {@code EN_JUEGO} y por un jugador de alguno de los dos equipos (RN-08). Para un {@link TipoGol#AUTOGOL}
 * el jugador es quien lo marca en contra y el gol suma al equipo contrario.
 */
@Getter
@Setter
@Entity
@Table(name = "gol")
public class Gol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gol")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_partido", nullable = false)
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_jugador", nullable = false)
    private Jugador jugador;

    /** Entre 1 y 130, con el tiempo añadido incluido (CHECK en la base de datos). */
    @Column(name = "minuto", nullable = false)
    private Integer minuto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoGol tipo;
}

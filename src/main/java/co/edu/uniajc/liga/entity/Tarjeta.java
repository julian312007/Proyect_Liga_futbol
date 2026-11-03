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
 * Tarjeta de un {@link Partido}. Tabla {@code tarjeta} ({@code V11__tarjeta.sql}). Solo se registra con el
 * partido {@code EN_JUEGO}. Dos amarillas a un jugador en el mismo partido generan una roja (RN-09).
 */
@Getter
@Setter
@Entity
@Table(name = "tarjeta")
public class Tarjeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarjeta")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_partido", nullable = false)
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_jugador", nullable = false)
    private Jugador jugador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoTarjeta tipo;

    /** Entre 1 y 130 (CHECK en la base de datos). */
    @Column(name = "minuto", nullable = false)
    private Integer minuto;
}

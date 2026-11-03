package co.edu.uniajc.liga.entity;

/**
 * Tipo de {@link Gol}. {@code NORMAL} y {@code PENAL} suman al goleador y al equipo del jugador;
 * {@code AUTOGOL} suma al equipo contrario y no cuenta para la tabla de goleadores.
 */
public enum TipoGol {
    NORMAL,
    PENAL,
    AUTOGOL
}

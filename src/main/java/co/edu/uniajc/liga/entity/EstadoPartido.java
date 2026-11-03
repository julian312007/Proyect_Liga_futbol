package co.edu.uniajc.liga.entity;

/**
 * Ciclo de vida de un {@link Partido}: {@code PROGRAMADO} → {@code EN_JUEGO} → {@code FINALIZADO}.
 * {@code FINALIZADO} es terminal (RN-12).
 */
public enum EstadoPartido {
    PROGRAMADO,
    EN_JUEGO,
    FINALIZADO
}

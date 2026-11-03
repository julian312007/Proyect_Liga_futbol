package co.edu.uniajc.liga.entity;

/**
 * Roles de la API. La autorización por rol se declara con {@code @PreAuthorize} en los
 * controladores; Spring Security los trata como autoridades {@code ROLE_ADMIN} y {@code ROLE_USER}.
 *
 * <p>El primer ingreso con Google siempre crea un {@link #USER}; el cliente nunca elige su rol. Un
 * {@link #ADMIN} solo nace del administrador inicial (variable {@code LIGA_ADMIN_EMAIL}) o, en el
 * futuro, de que otro ADMIN cambie el rol.
 */
public enum Rol {

    /** Administra la liga: crea, modifica y elimina, y ejecuta las acciones sobre los partidos. */
    ADMIN,

    /** Solo consulta información ({@code GET}). */
    USER
}

package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.Rol;

import java.time.Instant;

/**
 * Datos públicos de un usuario. Este {@code record} es lo único que sale de la API para representar a
 * un usuario: no incluye el {@code google_sub} (identificador interno de la cuenta de Google).
 *
 * @param id             identificador
 * @param email          correo electrónico (en minúsculas)
 * @param nombre         nombre para mostrar
 * @param pictureUrl     foto de perfil de Google; puede ser nula
 * @param rol            {@code ADMIN} o {@code USER}
 * @param activo         si puede ingresar
 * @param fechaCreacion  instante de creación de la cuenta
 * @param ultimoAcceso   último ingreso con Google; nulo si aún no ha ingresado
 */
public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String pictureUrl,
        Rol rol,
        boolean activo,
        Instant fechaCreacion,
        Instant ultimoAcceso) {
}

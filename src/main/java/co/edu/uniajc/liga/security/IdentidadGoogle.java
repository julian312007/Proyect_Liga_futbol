package co.edu.uniajc.liga.security;

import java.util.Locale;

/**
 * Identidad de un usuario según un ID token de Google ya verificado (firma, vigencia, emisor y
 * audiencia). Es lo único que {@link GoogleTokenVerifier} entrega al resto de la aplicación.
 *
 * <p>El correo se normaliza al construir el objeto (sin espacios y en minúsculas), igual que se guarda
 * en la base de datos. {@code emailVerificado} se entrega tal cual lo declara Google: decidir qué hacer
 * con un correo sin verificar es una regla de negocio y la aplica {@code AuthService}.
 *
 * @param sub             identificador estable de la cuenta de Google (claim {@code sub})
 * @param email           correo de la cuenta, en minúsculas
 * @param emailVerificado valor del claim {@code email_verified}; {@code false} si falta
 * @param nombre          nombre completo (claim {@code name}); puede ser nulo
 * @param pictureUrl      foto de perfil (claim {@code picture}); puede ser nula
 */
public record IdentidadGoogle(String sub, String email, boolean emailVerificado, String nombre, String pictureUrl) {

    public IdentidadGoogle {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        nombre = nombre == null ? null : nombre.strip();
        pictureUrl = pictureUrl == null ? null : pictureUrl.strip();
    }
}

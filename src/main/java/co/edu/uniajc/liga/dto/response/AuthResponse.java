package co.edu.uniajc.liga.dto.response;

import co.edu.uniajc.liga.entity.Rol;

/**
 * Respuesta de un ingreso con Google correcto: el JWT propio de la API.
 *
 * <p>El cliente debe enviar el token en cada solicitud: {@code Authorization: Bearer <token>}. No hay
 * <em>refresh token</em> ni lista de revocación: al vencer el token hay que volver a ingresar con
 * Google, y el cierre de sesión consiste en que el cliente descarte el token.
 *
 * @param token    JWT propio de la API (HS256); no es el ID token de Google
 * @param tipo     siempre {@value #TIPO_BEARER}
 * @param expiraEn segundos de vigencia que le quedan al token en el momento de emitirlo (como
 *                 {@code expires_in} de OAuth 2)
 * @param email    correo del usuario
 * @param nombre   nombre para mostrar
 * @param rol      rol del usuario
 */
public record AuthResponse(String token, String tipo, long expiraEn, String email, String nombre, Rol rol) {

    public static final String TIPO_BEARER = "Bearer";

    /** Oculta el token: un registro descuidado del objeto no debe filtrar una credencial vigente. */
    @Override
    public String toString() {
        return "AuthResponse[token=***, tipo=%s, expiraEn=%d, email=%s, nombre=%s, rol=%s]"
                .formatted(tipo, expiraEn, email, nombre, rol);
    }
}

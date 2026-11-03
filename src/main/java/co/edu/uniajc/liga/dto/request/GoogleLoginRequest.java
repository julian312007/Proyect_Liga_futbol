package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/google}: el ID token que el cliente obtuvo de Google Sign-In.
 *
 * <p>Es la única entrada del inicio de sesión. No lleva rol ni ningún dato del usuario: el servidor
 * obtiene identidad, correo y nombre del propio token, ya verificado, y decide el rol.
 *
 * @param idToken JWT firmado por Google (el "credential" que entrega el botón de Google Sign-In)
 */
public record GoogleLoginRequest(

        @NotBlank(message = "El idToken de Google es obligatorio")
        // Un ID token de Google mide 1 o 2 KB; el tope evita analizar cuerpos absurdamente grandes.
        @Size(max = 8192, message = "El idToken de Google no puede superar los 8192 caracteres")
        String idToken) {

    /** Oculta el token: es una credencial vigente y no debe aparecer en un registro (log) descuidado. */
    @Override
    public String toString() {
        return "GoogleLoginRequest[idToken=***]";
    }
}

package co.edu.uniajc.liga.security;

/**
 * Verifica un ID token de Google y devuelve la identidad que contiene.
 *
 * <p>Es una interfaz a propósito: la verificación real consulta las claves públicas de Google por
 * internet ({@link NimbusGoogleTokenVerifier}); las pruebas la sustituyen por un doble y así no
 * dependen de la red ni de un token real.
 */
public interface GoogleTokenVerifier {

    /**
     * Mensaje genérico de toda falla de autenticación: no distingue token falso, vencido, de otra
     * aplicación o de un correo sin verificar, para no darle pistas a quien prueba tokens.
     */
    String MENSAJE_CREDENCIALES_INVALIDAS = "Credenciales inválidas.";

    /**
     * @param idToken el JWT que entrega Google Sign-In al cliente
     * @return la identidad del token, ya validada su firma, vigencia, emisor y audiencia
     * @throws org.springframework.security.authentication.BadCredentialsException si el token es
     *         nulo, está mal formado, vencido, mal firmado, emitido para otra aplicación o por otro
     *         emisor, o no trae {@code sub} y {@code email}
     * @throws IllegalStateException si no se pudo consultar a Google (falla de infraestructura, no
     *         culpa del token)
     */
    IdentidadGoogle verificar(String idToken);
}

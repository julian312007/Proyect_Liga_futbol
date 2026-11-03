package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;

/**
 * Ingreso con Google y perfil de los usuarios de la API (requisito D-1).
 *
 * <p>Los errores se comunican con excepciones; el contrato de cada método dice cuáles:
 * <ul>
 *   <li>{@code AuthenticationException} de Spring Security (401): el ID token de Google no es válido,
 *       el correo no está verificado o no se puede enlazar con la cuenta existente. Siempre con un
 *       mensaje genérico.</li>
 *   <li>{@code AccessDeniedException} (403): la identidad de Google es válida pero la cuenta está
 *       desactivada.</li>
 *   <li>{@code RecursoNoEncontradoException} (404): el usuario autenticado ya no existe.</li>
 * </ul>
 */
public interface AuthService {

    /**
     * Registro e inicio de sesión en un solo paso. Verifica el ID token de Google; si es la primera vez
     * que ingresa esa cuenta crea el usuario con rol {@code USER} (el rol nunca lo decide el cliente), y
     * en todos los casos emite el JWT propio de la API.
     *
     * <p>Cómo se reconoce al usuario: primero por el {@code sub} de Google. Si no existe, por correo, pero
     * solo si esa fila todavía no tiene {@code sub} (es el caso del administrador inicial, creado con su
     * correo) y Google declara el correo como verificado; entonces se enlaza el {@code sub}.
     *
     * @throws org.springframework.security.core.AuthenticationException si el token no es válido, el
     *         correo no está verificado, o el correo ya está enlazado a otra cuenta de Google
     * @throws org.springframework.security.access.AccessDeniedException si el usuario está desactivado
     */
    AuthResponse iniciarSesionConGoogle(GoogleLoginRequest request);

    /** Perfil del usuario autenticado, identificado por su correo (el {@code sub} del JWT propio). */
    UsuarioResponse obtenerPerfil(String email);

    /** Edita el nombre del usuario autenticado. El correo y la foto los entrega Google. */
    UsuarioResponse actualizarPerfil(String email, ActualizarPerfilRequest request);

    /**
     * Crea el administrador inicial si no existe un usuario con ese correo. Es idempotente: lo invoca
     * {@code AdminInicializador} en cada arranque. Nunca cambia el rol de un usuario ya existente.
     *
     * @param email correo de Google del administrador (se normaliza a minúsculas)
     * @return {@code true} si lo creó; {@code false} si ya existía un usuario con ese correo
     */
    boolean crearAdministradorSiNoExiste(String email);
}

package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso con Google y perfil de usuario (requisito D-1).
 *
 * <p>Delgado a propósito: valida con {@code @Valid} y delega todo en {@link AuthService}. El acceso lo
 * decide {@code SecurityConfig}: {@code POST /google} es público; {@code /me} exige un token propio
 * válido de cualquier rol, por eso no lleva {@code @PreAuthorize}.
 *
 * <p>El flujo es: el cliente obtiene un ID token con Google Sign-In, lo envía a {@code /google} y recibe
 * el JWT propio de la API, que usa en el resto de solicitudes. No hay rutas de registro ni de login con
 * contraseña.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Ingreso con Google (JWT propio) y perfil del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Es a la vez registro e inicio de sesión: la primera vez que ingresa una cuenta de Google se crea el
     * usuario con rol USER. Responde 200 en ambos casos (no hay una URL propia del recurso creado que
     * justifique 201 con {@code Location}).
     */
    @PostMapping("/google")
    @SecurityRequirements
    @Operation(summary = "Ingresar con Google",
            description = "Recibe el ID token de Google Sign-In, lo verifica y devuelve un JWT propio para enviar como "
                    + "'Authorization: Bearer <token>'. Si es la primera vez, crea el usuario con rol USER. "
                    + "No hay cierre de sesión en el servidor: el cliente descarta el token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingreso correcto"),
            @ApiResponse(responseCode = "400", description = "Falta el idToken"),
            @ApiResponse(responseCode = "401", description = "Token de Google inválido, vencido, de otra aplicación "
                    + "o con correo sin verificar"),
            @ApiResponse(responseCode = "403", description = "La cuenta está desactivada")
    })
    public AuthResponse iniciarSesionConGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.iniciarSesionConGoogle(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Ver mi perfil", description = "Datos del usuario dueño del token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del usuario"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public UsuarioResponse perfil(@Parameter(hidden = true) Authentication authentication) {
        // El nombre de la autenticación es el sub del JWT propio: el correo del usuario.
        return authService.obtenerPerfil(authentication.getName());
    }

    @PutMapping("/me")
    @Operation(summary = "Editar mi perfil", description = "Cambia el nombre para mostrar. El correo lo da Google.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public UsuarioResponse actualizarPerfil(@Parameter(hidden = true) Authentication authentication,
                                            @Valid @RequestBody ActualizarPerfilRequest request) {
        return authService.actualizarPerfil(authentication.getName(), request);
    }
}

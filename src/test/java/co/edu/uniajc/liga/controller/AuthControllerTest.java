package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.AuthService;
import co.edu.uniajc.liga.support.TokensDePrueba;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de {@link AuthController} con {@code @WebMvcTest}: sin base de datos, sin red y sin Docker. El
 * servicio se simula; la seguridad es la real (cadena de filtros, JWT propio y handlers 401/403), con
 * usuarios en memoria. Cubre D-1: ingreso con Google correcto, token inválido 401, cuenta desactivada
 * 403, validación 400, perfil, y que ninguna respuesta exponga el ID token de Google ni secretos.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String ID_TOKEN = "id-token-ficticio-de-google";
    private static final String CUERPO_GOOGLE = "{\"idToken\": \"" + ID_TOKEN + "\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    // ================================================================ POST /google

    @Test
    @DisplayName("POST /google sin token y con un ID token válido: 200 con el JWT propio, correo, nombre y rol")
    void google_tokenValido_devuelve200ConElJwtPropio() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-propio", "Bearer", 3600, "ana@example.com", "Ana Pérez", Rol.USER));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-propio"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(3600))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.rol").value("USER"))
                // La respuesta nunca devuelve el ID token de Google ni identificadores internos.
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(jsonPath("$.googleSub").doesNotExist())
                .andExpect(content().string(not(containsString(ID_TOKEN))));

        verify(authService).iniciarSesionConGoogle(new GoogleLoginRequest(ID_TOKEN));
    }

    @Test
    @DisplayName("POST /google con un campo 'rol' en el cuerpo: se ignora; el servicio solo recibe el idToken")
    void google_conRolEnElCuerpo_seIgnora() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-propio", "Bearer", 3600, "ana@example.com", "Ana", Rol.USER));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"" + ID_TOKEN + "\", \"rol\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USER"));

        verify(authService).iniciarSesionConGoogle(new GoogleLoginRequest(ID_TOKEN));
    }

    @Test
    @DisplayName("POST /google con token de Google inválido, vencido o de otra aplicación: 401 genérico")
    void google_tokenInvalido_devuelve401Generico() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenThrow(new BadCredentialsException("Credenciales inválidas."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                // Solo se compara una parte ASCII: MockMvc puede decodificar mal las tildes de un JSON sin charset.
                .andExpect(jsonPath("$.mensaje", containsString("credenciales")))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString(ID_TOKEN))));
    }

    @Test
    @DisplayName("POST /google con una cuenta desactivada: 403 ACCESO_DENEGADO")
    void google_usuarioInactivo_devuelve403() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenThrow(new AccessDeniedException("La cuenta está desactivada"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                // El mensaje genérico no repite el texto interno de la excepción.
                .andExpect(content().string(not(containsStringIgnoringCase("desactivada"))));
    }

    @Test
    @DisplayName("POST /google sin el campo idToken: 400 con detalle y sin llamar al servicio")
    void google_sinIdToken_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("idToken:"))));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest(name = "idToken \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("POST /google con idToken vacío o en blanco: 400 y sin llamar al servicio")
    void google_idTokenVacio_devuelve400(String idToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"" + idToken + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /google con un idToken de más de 8192 caracteres: 400 y sin llamar al servicio")
    void google_idTokenGigante_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"" + "x".repeat(8193) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Ya no existen el registro ni el login con contraseña: /register y /login responden 401 sin token")
    void rutasDeContrasenaYaNoSonPublicas() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    // ======================================================================== /me

    @Test
    @DisplayName("GET /me sin token: 401 con formato ApiError y encabezado WWW-Authenticate")
    void perfil_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.ruta").value("/api/v1/auth/me"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /me con el ID token de Google en vez del JWT propio: 401 (no son intercambiables)")
    void perfil_conIdTokenDeGoogle_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + ID_TOKEN))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /me con token manipulado: 401")
    void perfil_conTokenManipulado_devuelve401() throws Exception {
        String manipulado = TokensDePrueba.conPayloadManipulado(
                jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + manipulado))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /me con el JWT propio: 200 con el perfil del dueño del token y sin google_sub")
    void perfil_conTokenValido_devuelve200() throws Exception {
        when(authService.obtenerPerfil(UsuariosDePruebaConfig.ANA)).thenReturn(respuestaDeUsuario());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.googleSub").doesNotExist());
    }

    @Test
    @DisplayName("PUT /me con el JWT propio: 200 y el servicio recibe el correo del token y solo el nombre")
    void actualizarPerfil_datosValidos_devuelve200() throws Exception {
        when(authService.actualizarPerfil(eq(UsuariosDePruebaConfig.ANA), any(ActualizarPerfilRequest.class)))
                .thenReturn(respuestaDeUsuario());

        // 'email' y 'rol' en el cuerpo se ignoran: el correo lo da Google y el rol no lo elige el cliente.
        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Ana Maria\", \"email\": \"otro@example.com\", \"rol\": \"ADMIN\"}"))
                .andExpect(status().isOk());

        verify(authService).actualizarPerfil(UsuariosDePruebaConfig.ANA, new ActualizarPerfilRequest("Ana Maria"));
    }

    @Test
    @DisplayName("PUT /me con el nombre en blanco: 400 y sin llamar al servicio")
    void actualizarPerfil_nombreEnBlanco_devuelve400() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("nombre:"))));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("PUT /me sin token: 401")
    void actualizarPerfil_sinToken_devuelve401() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Ana\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    // ======================================================================= Apoyo

    private static UsuarioResponse respuestaDeUsuario() {
        return new UsuarioResponse(1L, "ana@example.com", "Ana Pérez", "https://lh3.googleusercontent.com/a/foto",
                Rol.USER, true, Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-02T11:00:00Z"));
    }
}

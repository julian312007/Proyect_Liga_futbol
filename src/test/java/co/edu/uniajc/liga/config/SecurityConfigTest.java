package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.controller.AuthController;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.AuthService;
import co.edu.uniajc.liga.support.TokensDePrueba;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de acceso de {@link SecurityConfig} (D-2) con {@code @WebMvcTest}: sin base de datos ni Docker.
 * Se usa la cadena de filtros, el filtro JWT y los handlers reales, con tokens reales y usuarios en
 * memoria; solo el servicio de autenticación se simula.
 *
 * <p>Como en la Fase 1 aún no hay endpoints de negocio, la matriz se prueba contra un controlador
 * de prueba ({@link ControladorDePrueba}) que imita lo que harán los de las fases siguientes: una
 * lectura abierta a {@code USER} y {@code ADMIN} y una escritura solo para {@code ADMIN}.
 *
 * <p>Usuarios (ver {@link UsuariosDePruebaConfig}): {@code ana@example.com} (USER),
 * {@code root@example.com} (ADMIN) e {@code inactivo@example.com} (USER desactivado). El {@code sub} de los
 * tokens propios es el correo.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class, SecurityConfigTest.ControladorDePrueba.class})
@ActiveProfiles({"test", SecurityConfigTest.PERFIL_MATRIZ})
class SecurityConfigTest {

    /** Perfil que habilita {@link ControladorDePrueba}; solo lo activa esta clase. */
    static final String PERFIL_MATRIZ = "matriz-seguridad";

    private static final String RUTA_GOOGLE = "/api/v1/auth/google";
    private static final String CUERPO_GOOGLE = "{\"idToken\": \"id-token-ficticio-de-google\"}";
    private static final String LECTURA = "/api/v1/prueba-seguridad/lectura";
    private static final String ESCRITURA = "/api/v1/prueba-seguridad/escritura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /** El mismo secreto ficticio del perfil de pruebas, para poder fabricar tokens vencidos. */
    @Value("${liga.jwt.secret}")
    private String secretoJwt;

    @MockitoBean
    private AuthService authService;

    // ===================================================== 401: sin autenticación válida

    @Test
    @DisplayName("Sin token en una ruta protegida: 401 con ApiError, WWW-Authenticate y sin cookie de sesión")
    void sinToken_devuelve401() throws Exception {
        MvcResult resultado = mockMvc.perform(get(LECTURA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.ruta").value(LECTURA))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        // API sin estado: ninguna solicitud crea una sesión HTTP.
        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("Ruta inexistente sin token: también 401 (no se revela qué rutas existen)")
    void rutaInexistenteSinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token vencido: 401")
    void tokenVencido_devuelve401() throws Exception {
        String vencido = TokensDePrueba.vencido(secretoJwt, UsuariosDePruebaConfig.ANA, Rol.USER);

        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("Token con el rol editado (USER -> ADMIN) y la firma original: 401, no hay escalada de privilegios")
    void tokenManipulado_devuelve401() throws Exception {
        String manipulado = TokensDePrueba.conPayloadManipulado(jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER));

        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + manipulado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token con la firma alterada: 401")
    void tokenConFirmaAlterada_devuelve401() throws Exception {
        String alterado = TokensDePrueba.conFirmaAlterada(jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER));

        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token firmado con otra clave: 401")
    void tokenDeOtraClave_devuelve401() throws Exception {
        String ajeno = TokensDePrueba.firmadoConOtraClave(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);

        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token sin firma (alg none) que dice ser ADMIN: 401")
    void tokenSinFirma_devuelve401() throws Exception {
        String sinFirma = TokensDePrueba.sinFirma(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);

        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + sinFirma))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token válido de un usuario que no existe: 401")
    void tokenDeUsuarioInexistente_devuelve401() throws Exception {
        String token = jwtService.generarToken("fantasma@example.com", Rol.ADMIN);

        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token válido de un usuario desactivado: 401")
    void tokenDeUsuarioDesactivado_devuelve401() throws Exception {
        String token = jwtService.generarToken(UsuariosDePruebaConfig.INACTIVO, Rol.USER);

        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "encabezado \"{0}\"")
    @ValueSource(strings = {"Basic YW5hOmNsYXZl", "Bearer", "Bearer ", "Token abc", "abc.def.ghi"})
    @DisplayName("Encabezado Authorization con esquema incorrecto o sin token: 401")
    void encabezadoMalFormado_devuelve401(String encabezado) throws Exception {
        mockMvc.perform(get(LECTURA).header("Authorization", encabezado))
                .andExpect(status().isUnauthorized());
    }

    // ======================================================= 403: autenticado sin el rol

    @Test
    @DisplayName("USER en un endpoint de escritura (solo ADMIN): 403 con ApiError")
    void userEnEscritura_devuelve403() throws Exception {
        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + tokenDe(UsuariosDePruebaConfig.ANA, Rol.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.ruta").value(ESCRITURA));
    }

    // ============================================================ 2xx: con el rol correcto

    @Test
    @DisplayName("USER en un endpoint de lectura: 200")
    void userEnLectura_devuelve200() throws Exception {
        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + tokenDe(UsuariosDePruebaConfig.ANA, Rol.USER)))
                .andExpect(status().isOk())
                .andExpect(content().string("lectura"));
    }

    @Test
    @DisplayName("ADMIN en un endpoint de lectura: 200")
    void adminEnLectura_devuelve200() throws Exception {
        mockMvc.perform(get(LECTURA).header("Authorization", "Bearer " + tokenDe(UsuariosDePruebaConfig.ROOT, Rol.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN en un endpoint de escritura: 2xx, y sin token CSRF (la API es sin estado)")
    void adminEnEscritura_devuelve200SinCsrf() throws Exception {
        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + tokenDe(UsuariosDePruebaConfig.ROOT, Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string("escritura"));
    }

    @Test
    @DisplayName("El rol efectivo sale del usuario, no del claim: token con rol ADMIN de un USER sigue dando 403")
    void claimRolAdminDeUnUser_noDaPermisos() throws Exception {
        // Token legítimo (lo firma la aplicación) pero que afirma ADMIN para 'ana', que es USER.
        String token = jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.ADMIN);

        mockMvc.perform(post(ESCRITURA).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ================================================================ Rutas públicas

    @Test
    @DisplayName("POST /google es público: sin token no responde 401 ni 403")
    void googleEsPublico() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenReturn(new AuthResponse("t", "Bearer", 3600, UsuariosDePruebaConfig.ANA, "Ana", Rol.USER));

        mockMvc.perform(post(RUTA_GOOGLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un token propio vencido en la ruta pública no la bloquea (el filtro lo ignora)")
    void tokenVencidoEnRutaPublica_noBloquea() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class)))
                .thenReturn(new AuthResponse("t", "Bearer", 3600, UsuariosDePruebaConfig.ANA, "Ana", Rol.USER));
        String vencido = TokensDePrueba.vencido(secretoJwt, UsuariosDePruebaConfig.ANA, Rol.USER);

        mockMvc.perform(post(RUTA_GOOGLE)
                        .header("Authorization", "Bearer " + vencido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Solo el método POST de /google es público: GET /google sin token da 401")
    void getEnGoogle_noEsPublico() throws Exception {
        mockMvc.perform(get(RUTA_GOOGLE))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/login"})
    @DisplayName("Las rutas de registro y login con contraseña ya no existen ni son públicas: 401 sin token")
    void rutasDeContrasena_noSonPublicas(String ruta) throws Exception {
        mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/autenticacion", "/swagger-ui.html", "/swagger-ui/index.html"})
    @DisplayName("Swagger y OpenAPI son públicos: sin token no responden 401 ni 403")
    void swaggerEsPublico(String ruta) throws Exception {
        // En esta prueba springdoc no está cargado, así que la ruta termina en 404; lo que se
        // comprueba es que la seguridad no la bloquea.
        int estado = mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus();

        assertThat(estado).isNotIn(401, 403);
    }

    // ============================================== Mismo formato en los dos caminos del 401

    @Test
    @DisplayName("El 401 de la cadena de filtros y el del controlador (token de Google inválido) tienen el mismo código y mensaje")
    void formato401_delFiltroYDelControlador_coinciden() throws Exception {
        when(authService.iniciarSesionConGoogle(any(GoogleLoginRequest.class))).thenThrow(new BadCredentialsException("x"));

        String desdeElFiltro = mockMvc.perform(get(LECTURA)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String desdeElControlador = mockMvc.perform(post(RUTA_GOOGLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_GOOGLE))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(desdeElFiltro, "$.codigo"))
                .isEqualTo(JsonPath.<String>read(desdeElControlador, "$.codigo"))
                .isEqualTo("NO_AUTENTICADO");
        // Los dos textos son idénticos: si se cambia uno en GlobalExceptionHandler, esta prueba avisa.
        assertThat(JsonPath.<String>read(desdeElFiltro, "$.mensaje"))
                .isEqualTo(JsonPath.<String>read(desdeElControlador, "$.mensaje"));
    }

    @Test
    @DisplayName("El 401 es idéntico con token ausente y con token vencido: no revela el motivo")
    void mensaje401_noDistingueTokenAusenteDeVencido() throws Exception {
        String sinToken = mockMvc.perform(get(LECTURA)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String conTokenVencido = mockMvc.perform(get(LECTURA)
                        .header("Authorization", "Bearer " + TokensDePrueba.vencido(secretoJwt, UsuariosDePruebaConfig.ANA, Rol.USER)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsStringIgnoringCase("vencid"))))
                .andExpect(content().string(not(containsStringIgnoringCase("expir"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(conTokenVencido, "$.codigo"))
                .isEqualTo(JsonPath.<String>read(sinToken, "$.codigo"));
        assertThat(JsonPath.<String>read(conTokenVencido, "$.mensaje"))
                .isEqualTo(JsonPath.<String>read(sinToken, "$.mensaje"));
    }

    // ===================================================================== Apoyo

    private String tokenDe(String subject, Rol rol) {
        return jwtService.generarToken(subject, rol);
    }

    /**
     * Imita lo que harán los controladores de las fases siguientes. Spring MVC 7 solo reconoce clases con
     * {@code @Controller}, y eso lo haría visible al escaneo de componentes de las pruebas
     * {@code @SpringBootTest}; por eso solo existe con el perfil {@code matriz-seguridad}, que activa
     * únicamente esta clase de prueba, y se agrega con {@code @Import}.
     */
    @RestController
    @RequestMapping("/api/v1/prueba-seguridad")
    @Profile(PERFIL_MATRIZ)
    public static class ControladorDePrueba {

        @GetMapping("/lectura")
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        public String lectura() {
            return "lectura";
        }

        @PostMapping("/escritura")
        @PreAuthorize("hasRole('ADMIN')")
        public String escritura() {
            return "escritura";
        }
    }
}

package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.AdminInicializador;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.infra.TestcontainersConfig;
import co.edu.uniajc.liga.repository.UsuarioRepository;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import co.edu.uniajc.liga.support.GoogleTokenVerifierFalso;
import co.edu.uniajc.liga.support.GoogleVerificadorFalsoConfig;
import co.edu.uniajc.liga.support.TokensDePrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de la Fase 1 (D-1 y D-2) con la aplicación completa, PostgreSQL real
 * (Testcontainers, Flyway con la migración V2 y {@code ddl-auto=validate}) y la cadena de seguridad real.
 * Requiere Docker; corre en {@code mvn verify}.
 *
 * <p>La única pieza simulada es el verificador de ID tokens de Google ({@link GoogleTokenVerifierFalso}):
 * no hay red ni cuenta de Google real. La validación real de firma, emisor, audiencia y vigencia se prueba
 * en {@code NimbusGoogleTokenVerifierTest}.
 *
 * <p>Que el contexto arranque ya demuestra que la entidad {@code Usuario} coincide con la tabla
 * {@code usuario} (Hibernate valida el esquema al iniciar). Todos los datos son ficticios; el
 * administrador inicial se configura con una propiedad de la propia prueba.
 */
@SpringBootTest(properties = "liga.admin.email=admin.it@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, GoogleVerificadorFalsoConfig.class})
class AuthFlujoIT {

    private static final String CORREO_ADMIN = "admin.it@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AdminInicializador adminInicializador;

    @Autowired
    private GoogleTokenVerifierFalso googleFalso;

    @Autowired
    private DataSource dataSource;

    @Value("${liga.jwt.secret}")
    private String secretoJwt;

    // ============================================================ Flujo completo

    @Test
    @DisplayName("Ingreso con Google -> JWT propio -> endpoint protegido: el flujo completo de D-1 y D-2")
    void flujoCompleto_googleYEndpointProtegido() throws Exception {
        String sub = subUnico();
        String correo = correoUnico();
        String idToken = idTokenDe(sub, correo, true, "Ana Pérez");

        // 1. Sin token, el endpoint protegido responde 401.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        // 2. Primer ingreso: 200, se crea el usuario con rol USER y se devuelve el JWT propio.
        String cuerpo = ingresar(idToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(3600))
                .andExpect(jsonPath("$.email").value(correo))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(content().string(not(containsString(idToken))))
                .andExpect(content().string(not(containsString(sub))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String jwtPropio = JsonPath.read(cuerpo, "$.token");
        assertThat(jwtPropio).isNotBlank().isNotEqualTo(idToken);

        // 3. Con el JWT propio, el endpoint protegido responde 200 con el perfil (sin el sub de Google).
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwtPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(correo))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(content().string(not(containsString(sub))));

        // 4. En la base de datos: sub enlazado, rol USER y último acceso registrado.
        Usuario guardado = usuarioRepository.findByEmail(correo).orElseThrow();
        assertThat(guardado.getGoogleSub()).isEqualTo(sub);
        assertThat(guardado.getRol()).isEqualTo(Rol.USER);
        assertThat(guardado.getNombre()).isEqualTo("Ana Pérez");
        assertThat(guardado.getUltimoAcceso()).isNotNull();
    }

    @Test
    @DisplayName("Un segundo ingreso de la misma cuenta no crea otro usuario")
    void segundoIngreso_noDuplicaAlUsuario() throws Exception {
        String idToken = idTokenDe(subUnico(), correoUnico(), true, "Ana");
        ingresar(idToken).andExpect(status().isOk());
        long antes = usuarioRepository.count();

        ingresar(idToken).andExpect(status().isOk());

        assertThat(usuarioRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("ID token que Google no reconoce: 401 genérico y no se crea ningún usuario")
    void idTokenDesconocido_devuelve401() throws Exception {
        long antes = usuarioRepository.count();

        ingresar("este-id-token-no-existe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(usuarioRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Correo sin verificar en Google: 401 y no se crea ningún usuario")
    void correoSinVerificar_devuelve401() throws Exception {
        String correo = correoUnico();

        ingresar(idTokenDe(subUnico(), correo, false, "Ana")).andExpect(status().isUnauthorized());

        assertThat(usuarioRepository.findByEmail(correo)).isEmpty();
    }

    @Test
    @DisplayName("Sin el campo idToken: 400")
    void sinIdToken_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // ============================================================ Tokens propios

    @Test
    @DisplayName("Tokens propios manipulados, de otra clave, vencidos o sin firma, y el ID token de Google: 401")
    void tokensInvalidos_devuelven401() throws Exception {
        String correo = correoUnico();
        String idToken = idTokenDe(subUnico(), correo, true, "Ana");
        String valido = JsonPath.read(
                ingresar(idToken).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");

        for (String invalido : new String[]{
                TokensDePrueba.conPayloadManipulado(valido),
                TokensDePrueba.conFirmaAlterada(valido),
                TokensDePrueba.firmadoConOtraClave(correo, Rol.USER),
                TokensDePrueba.vencido(secretoJwt, correo, Rol.USER),
                TokensDePrueba.sinFirma(correo, Rol.ADMIN),
                idToken}) {
            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + invalido))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================ Desactivación

    @Test
    @DisplayName("Un usuario desactivado: su JWT vigente deja de servir de inmediato (401) y no puede volver a ingresar (403)")
    void usuarioDesactivado_pierdeElAccesoDeInmediato() throws Exception {
        String correo = correoUnico();
        String idToken = idTokenDe(subUnico(), correo, true, "Ana");
        String jwtPropio = JsonPath.read(
                ingresar(idToken).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwtPropio))
                .andExpect(status().isOk());

        Usuario usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        usuario.setActivo(false);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwtPropio))
                .andExpect(status().isUnauthorized());
        ingresar(idToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // ============================================================ Enlace por correo

    @Test
    @DisplayName("El administrador inicial (solo con correo) se enlaza en su primer ingreso y conserva el rol ADMIN")
    void administradorInicial_seEnlazaEnSuPrimerIngreso() throws Exception {
        // Creado al arrancar por AdminInicializador, sin google_sub.
        Usuario antes = usuarioRepository.findByEmail(CORREO_ADMIN).orElseThrow();
        assertThat(antes.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(antes.getGoogleSub()).isNull();

        String sub = subUnico();
        String cuerpo = ingresar(idTokenDe(sub, CORREO_ADMIN, true, "Admin de Pruebas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Usuario despues = usuarioRepository.findByEmail(CORREO_ADMIN).orElseThrow();
        assertThat(despues.getId()).isEqualTo(antes.getId());
        assertThat(despues.getGoogleSub()).isEqualTo(sub);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + JsonPath.<String>read(cuerpo, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));

        // Otra cuenta de Google con el mismo correo (por ejemplo, un buzón reasignado) no se queda con la fila.
        ingresar(idTokenDe(subUnico(), CORREO_ADMIN, true, "Impostor")).andExpect(status().isUnauthorized());
        assertThat(usuarioRepository.findByEmail(CORREO_ADMIN).orElseThrow().getGoogleSub()).isEqualTo(sub);
    }

    @Test
    @DisplayName("Otra cuenta de Google con el correo de un usuario ya enlazado: 401, no se reasigna")
    void correoDeUnUsuarioEnlazado_noSeReasigna() throws Exception {
        String correo = correoUnico();
        String subOriginal = subUnico();
        ingresar(idTokenDe(subOriginal, correo, true, "Ana")).andExpect(status().isOk());

        ingresar(idTokenDe(subUnico(), correo, true, "Otra persona")).andExpect(status().isUnauthorized());

        assertThat(usuarioRepository.findByEmail(correo).orElseThrow().getGoogleSub()).isEqualTo(subOriginal);
    }

    @Test
    @DisplayName("Si el correo cambia en Google, el usuario se reconoce por su sub; los tokens propios previos dejan de servir")
    void correoCambiaEnGoogle_seReconocePorSub() throws Exception {
        String sub = subUnico();
        String correoViejo = correoUnico();
        String correoNuevo = correoUnico();
        String jwtViejo = JsonPath.read(ingresar(idTokenDe(sub, correoViejo, true, "Ana"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        long antes = usuarioRepository.count();

        String jwtNuevo = JsonPath.read(ingresar(idTokenDe(sub, correoNuevo, true, "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(correoNuevo))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");

        assertThat(usuarioRepository.count()).isEqualTo(antes);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwtNuevo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(correoNuevo));
        // El sub del token viejo era el correo viejo: ya no existe, hay que volver a ingresar.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwtViejo))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================== Perfil

    @Test
    @DisplayName("PUT /me cambia solo el nombre: el correo y el rol enviados en el cuerpo se ignoran")
    void actualizarPerfil_cambiaSoloElNombre() throws Exception {
        String correo = correoUnico();
        String jwtPropio = JsonPath.read(ingresar(idTokenDe(subUnico(), correo, true, "Ana"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");

        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtPropio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Nombre Nuevo\", \"email\": \"otro@example.com\", \"rol\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.email").value(correo))
                .andExpect(jsonPath("$.rol").value("USER"));

        Usuario guardado = usuarioRepository.findByEmail(correo).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Nombre Nuevo");
        assertThat(guardado.getRol()).isEqualTo(Rol.USER);
        assertThat(usuarioRepository.findByEmail("otro@example.com")).isEmpty();
    }

    // ================================================ Administrador y base de datos

    @Test
    @DisplayName("El inicializador del administrador es idempotente: ejecutarlo de nuevo no duplica ni falla")
    void administradorInicial_esIdempotente() {
        long antes = usuarioRepository.count();

        adminInicializador.run(new DefaultApplicationArguments());
        adminInicializador.run(new DefaultApplicationArguments());

        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findByEmail(CORREO_ADMIN).orElseThrow().getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    @DisplayName("Los índices únicos de la base de datos son la última defensa: google_sub y email repetidos violan la restricción")
    void indicesUnicos_rechazanRepetidosAunqueSeSalteElServicio() throws Exception {
        String sub = subUnico();
        String correo = correoUnico();
        ingresar(idTokenDe(sub, correo, true, "Ana")).andExpect(status().isOk());

        Usuario mismoSub = nuevo(correoUnico(), sub);
        Usuario mismoCorreo = nuevo(correo, subUnico());

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(mismoSub))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(mismoCorreo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Varios usuarios pueden tener google_sub nulo (el administrador inicial antes de su primer ingreso)")
    void googleSubNulo_noEsUnico() {
        usuarioRepository.saveAndFlush(nuevo(correoUnico(), null));
        usuarioRepository.saveAndFlush(nuevo(correoUnico(), null));
    }

    @Test
    @DisplayName("Flyway aplicó la migración V2 (tabla usuario)")
    void flywayAplicoLaMigracionV2() throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             Statement consulta = conexion.createStatement();
             ResultSet resultado = consulta.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = TRUE")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getInt(1)).isEqualTo(1);
        }
    }

    // ======================================================================= Apoyo

    private ResultActions ingresar(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\": \"" + idToken + "\"}"));
    }

    /** Registra en el verificador falso una identidad y devuelve el "ID token" que la representa. */
    private String idTokenDe(String sub, String correo, boolean emailVerificado, String nombre) {
        String idToken = "id-token-ficticio-" + UUID.randomUUID();
        googleFalso.registrar(idToken, new IdentidadGoogle(sub, correo, emailVerificado, nombre,
                "https://lh3.googleusercontent.com/a/foto-ficticia"));
        return idToken;
    }

    private static Usuario nuevo(String correo, String googleSub) {
        Usuario usuario = new Usuario();
        usuario.setEmail(correo);
        usuario.setGoogleSub(googleSub);
        usuario.setNombre("Usuario de prueba");
        return usuario;
    }

    private static String subUnico() {
        return "google-sub-" + UUID.randomUUID();
    }

    private static String correoUnico() {
        return "u" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }
}

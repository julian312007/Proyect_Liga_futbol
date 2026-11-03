package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de acceso y contrato HTTP común de los cuatro controladores de catálogo (Estadio, Arbitro,
 * Entrenador y Liga). Cada {@code *ControllerTest} extiende esta clase, aporta su propio
 * {@code @WebMvcTest}/{@code @Import}/{@code @MockitoBean} y sus pruebas de reglas, y hereda estas pruebas.
 *
 * <p>Matriz por endpoint (D-2): sin token 401, {@code USER} en escritura 403, {@code ADMIN} 2xx,
 * {@code USER}/{@code ADMIN} en lectura 200. La seguridad es la real (cadena de filtros, JWT propio y handlers
 * 401/403); solo el servicio se simula.
 *
 * <p>Nota: Spring resuelve y valida el cuerpo ({@code @Valid}) antes de que actúe {@code @PreAuthorize}, así
 * que un {@code USER} que envía un cuerpo inválido recibe 400 y no 403; por eso las pruebas de 403 envían un
 * cuerpo válido.
 *
 * <p>Esta clase no termina en {@code Test} a propósito: no es una prueba por sí misma.
 */
abstract class MatrizRolesCatalogo {

    /** Los cinco endpoints del CRUD, con el estado HTTP que devuelve cada uno cuando todo sale bien. */
    enum Operacion {
        LISTAR(HttpMethod.GET, "", 200),
        OBTENER(HttpMethod.GET, "/1", 200),
        CREAR(HttpMethod.POST, "", 201),
        ACTUALIZAR(HttpMethod.PUT, "/1", 200),
        ELIMINAR(HttpMethod.DELETE, "/1", 204);

        final HttpMethod metodo;
        final String sufijo;
        final int estadoExitoso;

        Operacion(HttpMethod metodo, String sufijo, int estadoExitoso) {
            this.metodo = metodo;
            this.sufijo = sufijo;
            this.estadoExitoso = estadoExitoso;
        }

        boolean llevaCuerpo() {
            return metodo == HttpMethod.POST || metodo == HttpMethod.PUT;
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    /** Ruta del recurso, por ejemplo {@code /api/v1/estadios}. */
    protected abstract String rutaBase();

    /** Cuerpo JSON válido para crear o reemplazar. */
    protected abstract String cuerpoValido();

    /**
     * Configura el servicio simulado para que todas sus operaciones terminen bien con el recurso de id 1;
     * {@code listar} devuelve una única página de tamaño 20 con ese recurso (total 1 elemento, 1 página).
     */
    protected abstract void prepararServicio();

    /** Comprueba que la solicitud no llegó al servicio. */
    protected abstract void verificarServicioSinUso();

    @BeforeEach
    void prepararElServicioSimulado() {
        prepararServicio();
    }

    // ------------------------------------------------------------------ apoyo

    protected String admin() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    protected String user() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    protected ResultActions llamar(Operacion operacion, String autorizacion) throws Exception {
        return enviar(operacion.metodo, rutaBase() + operacion.sufijo,
                operacion.llevaCuerpo() ? cuerpoValido() : null, autorizacion);
    }

    /** Envía una solicitud; {@code cuerpo} y {@code autorizacion} pueden ser {@code null}. */
    protected ResultActions enviar(HttpMethod metodo, String ruta, String cuerpo, String autorizacion) throws Exception {
        MockHttpServletRequestBuilder solicitud = request(metodo, ruta);
        if (cuerpo != null) {
            solicitud.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        return mockMvc.perform(solicitud);
    }

    // ------------------------------------------------------- matriz de roles

    @ParameterizedTest(name = "{0}")
    @EnumSource(Operacion.class)
    @DisplayName("Sin token: todos los endpoints responden 401 NO_AUTENTICADO y no llegan al servicio")
    void sinToken_devuelve401(Operacion operacion) throws Exception {
        llamar(operacion, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verificarServicioSinUso();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Operacion.class, names = {"LISTAR", "OBTENER"})
    @DisplayName("USER en lectura (GET): 200")
    void user_enLectura_devuelve200(Operacion operacion) throws Exception {
        llamar(operacion, user()).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Operacion.class, names = {"CREAR", "ACTUALIZAR", "ELIMINAR"})
    @DisplayName("USER en escritura (POST, PUT, DELETE): 403 ACCESO_DENEGADO y no llega al servicio")
    void user_enEscritura_devuelve403(Operacion operacion) throws Exception {
        llamar(operacion, user())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verificarServicioSinUso();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Operacion.class)
    @DisplayName("ADMIN en todos los endpoints: 2xx (200, 201 o 204 según el endpoint), sin token CSRF")
    void admin_enTodo_devuelve2xx(Operacion operacion) throws Exception {
        llamar(operacion, admin()).andExpect(status().is(operacion.estadoExitoso));
    }

    // ------------------------------------------------------ contrato HTTP común

    @Test
    @DisplayName("POST responde 201 con la cabecera Location apuntando al recurso creado y el recurso en el cuerpo")
    void crear_devuelve201ConLocation() throws Exception {
        llamar(Operacion.CREAR, admin())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(rutaBase() + "/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE responde 204 sin cuerpo")
    void eliminar_devuelve204SinCuerpo() throws Exception {
        llamar(Operacion.ELIMINAR, admin())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET de la colección devuelve la página con la forma documentada (contenido, pagina, tamanio, totales)")
    void listar_devuelveLaFormaDeLaPagina() throws Exception {
        llamar(Operacion.LISTAR, admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(1));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Operacion.class, names = {"OBTENER", "ACTUALIZAR", "ELIMINAR"})
    @DisplayName("Un id que no es un número (/abc): 400 PARAMETRO_INVALIDO, no un 500")
    void idNoNumerico_devuelve400(Operacion operacion) throws Exception {
        enviar(operacion.metodo, rutaBase() + "/abc", operacion.llevaCuerpo() ? cuerpoValido() : null, admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verificarServicioSinUso();
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON válido: 400 CUERPO_INVALIDO")
    void cuerpoMalFormado_devuelve400() throws Exception {
        enviar(HttpMethod.POST, rutaBase(), "{esto no es json", admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verificarServicioSinUso();
    }

    @Test
    @DisplayName("POST sin cuerpo: 400 CUERPO_INVALIDO")
    void crearSinCuerpo_devuelve400() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, rutaBase())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verificarServicioSinUso();
    }
}

package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.ArbitroService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code ArbitroController} con {@code @WebMvcTest}: matriz de roles heredada de {@link MatrizRolesCatalogo} más
 * la validación del cuerpo (años de experiencia de cero o más, nombre obligatorio) y los errores 404 y 409.
 */
@WebMvcTest(ArbitroController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class ArbitroControllerTest extends MatrizRolesCatalogo {

    private static final String RUTA = "/api/v1/arbitros";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Wilmar Roldán", "nacionalidad": "Colombia", "aniosExperiencia": 15}""";
    private static final ArbitroResponse ARBITRO = new ArbitroResponse(1L, "Wilmar Roldán", "Colombia", 15);

    @MockitoBean
    private ArbitroService arbitroService;

    @Override
    protected String rutaBase() {
        return RUTA;
    }

    @Override
    protected String cuerpoValido() {
        return CUERPO_VALIDO;
    }

    @Override
    protected void prepararServicio() {
        when(arbitroService.listar(any())).thenReturn(new PaginaResponse<>(List.of(ARBITRO), 0, 20, 1, 1));
        when(arbitroService.obtener(1L)).thenReturn(ARBITRO);
        when(arbitroService.crear(any(ArbitroRequest.class))).thenReturn(ARBITRO);
        when(arbitroService.actualizar(eq(1L), any(ArbitroRequest.class))).thenReturn(ARBITRO);
    }

    @Override
    protected void verificarServicioSinUso() {
        verifyNoInteractions(arbitroService);
    }

    @Test
    @DisplayName("Años de experiencia 0 (el mínimo) y sin nacionalidad es válido: 201")
    void aniosCero_sinNacionalidad_devuelve201() throws Exception {
        enviar(HttpMethod.POST, RUTA, """
                {"nombre": "Árbitro Nuevo", "aniosExperiencia": 0}""", admin())
                .andExpect(status().isCreated());

        verify(arbitroService).crear(new ArbitroRequest("Árbitro Nuevo", null, 0));
    }

    static Stream<Arguments> cuerposInvalidos() {
        return Stream.of(
                Arguments.of("años negativos", """
                        {"nombre": "Wilmar", "nacionalidad": "Colombia", "aniosExperiencia": -1}""", "aniosExperiencia"),
                Arguments.of("años ausentes", """
                        {"nombre": "Wilmar", "nacionalidad": "Colombia"}""", "aniosExperiencia"),
                Arguments.of("nombre vacío", """
                        {"nombre": "", "nacionalidad": "Colombia", "aniosExperiencia": 3}""", "nombre"),
                Arguments.of("nombre ausente", """
                        {"nacionalidad": "Colombia", "aniosExperiencia": 3}""", "nombre"),
                Arguments.of("nacionalidad de más de 60 caracteres", """
                        {"nombre": "Wilmar", "nacionalidad": "%s", "aniosExperiencia": 3}""".formatted("x".repeat(61)),
                        "nacionalidad"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cuerposInvalidos")
    @DisplayName("Cuerpo inválido: 400 VALIDACION con el campo en los detalles (POST y PUT)")
    void cuerpoInvalido_devuelve400(String descripcion, String cuerpo, String campo) throws Exception {
        for (HttpMethod metodo : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            enviar(metodo, metodo == HttpMethod.POST ? RUTA : RUTA + "/1", cuerpo, admin())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles", hasItem(startsWith(campo + ": "))));
        }

        verifyNoInteractions(arbitroService);
    }

    @Test
    @DisplayName("Un árbitro inexistente: GET, PUT y DELETE responden 404 RECURSO_NO_ENCONTRADO")
    void arbitroInexistente_devuelve404() throws Exception {
        when(arbitroService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Arbitro", 99L));
        when(arbitroService.actualizar(eq(99L), any(ArbitroRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Arbitro", 99L));
        doThrow(new RecursoNoEncontradoException("Arbitro", 99L)).when(arbitroService).eliminar(99L);

        enviar(HttpMethod.GET, RUTA + "/99", null, user())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        enviar(HttpMethod.PUT, RUTA + "/99", CUERPO_VALIDO, admin()).andExpect(status().isNotFound());
        enviar(HttpMethod.DELETE, RUTA + "/99", null, admin()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE de un árbitro con partidos: 409 con el código del conflicto")
    void eliminar_conPartidos_devuelve409() throws Exception {
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "El árbitro tiene partidos"))
                .when(arbitroService).eliminar(1L);

        enviar(HttpMethod.DELETE, RUTA + "/1", null, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("GET /arbitros devuelve el árbitro con todos sus campos")
    void listar_devuelveLosCamposDelArbitro() throws Exception {
        enviar(HttpMethod.GET, RUTA, null, user())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Wilmar Roldán"))
                .andExpect(jsonPath("$.contenido[0].nacionalidad").value("Colombia"))
                .andExpect(jsonPath("$.contenido[0].aniosExperiencia").value(15));
    }
}

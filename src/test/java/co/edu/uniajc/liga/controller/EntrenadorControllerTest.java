package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.EntrenadorService;
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
 * {@code EntrenadorController} con {@code @WebMvcTest}: matriz de roles heredada de {@link MatrizRolesCatalogo}
 * más la validación del cuerpo (nombre obligatorio) y los errores 404 y 409.
 */
@WebMvcTest(EntrenadorController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class EntrenadorControllerTest extends MatrizRolesCatalogo {

    private static final String RUTA = "/api/v1/entrenadores";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Alexandre Guimarães", "nacionalidad": "Brasil"}""";
    private static final EntrenadorResponse ENTRENADOR = new EntrenadorResponse(1L, "Alexandre Guimarães", "Brasil");

    @MockitoBean
    private EntrenadorService entrenadorService;

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
        when(entrenadorService.listar(any())).thenReturn(new PaginaResponse<>(List.of(ENTRENADOR), 0, 20, 1, 1));
        when(entrenadorService.obtener(1L)).thenReturn(ENTRENADOR);
        when(entrenadorService.crear(any(EntrenadorRequest.class))).thenReturn(ENTRENADOR);
        when(entrenadorService.actualizar(eq(1L), any(EntrenadorRequest.class))).thenReturn(ENTRENADOR);
    }

    @Override
    protected void verificarServicioSinUso() {
        verifyNoInteractions(entrenadorService);
    }

    @Test
    @DisplayName("Nombre sin nacionalidad es válido: 201")
    void sinNacionalidad_devuelve201() throws Exception {
        enviar(HttpMethod.POST, RUTA, """
                {"nombre": "Entrenador Nuevo"}""", admin())
                .andExpect(status().isCreated());

        verify(entrenadorService).crear(new EntrenadorRequest("Entrenador Nuevo", null));
    }

    static Stream<Arguments> cuerposInvalidos() {
        return Stream.of(
                Arguments.of("nombre vacío", """
                        {"nombre": "", "nacionalidad": "Brasil"}""", "nombre"),
                Arguments.of("nombre solo espacios", """
                        {"nombre": "   ", "nacionalidad": "Brasil"}""", "nombre"),
                Arguments.of("nombre ausente", """
                        {"nacionalidad": "Brasil"}""", "nombre"),
                Arguments.of("nombre de más de 100 caracteres", """
                        {"nombre": "%s"}""".formatted("x".repeat(101)), "nombre"),
                Arguments.of("nacionalidad de más de 60 caracteres", """
                        {"nombre": "Alexandre", "nacionalidad": "%s"}""".formatted("x".repeat(61)), "nacionalidad"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cuerposInvalidos")
    @DisplayName("Cuerpo inválido (nombre obligatorio): 400 VALIDACION con el campo en los detalles (POST y PUT)")
    void cuerpoInvalido_devuelve400(String descripcion, String cuerpo, String campo) throws Exception {
        for (HttpMethod metodo : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            enviar(metodo, metodo == HttpMethod.POST ? RUTA : RUTA + "/1", cuerpo, admin())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles", hasItem(startsWith(campo + ": "))));
        }

        verifyNoInteractions(entrenadorService);
    }

    @Test
    @DisplayName("Un entrenador inexistente: GET, PUT y DELETE responden 404 RECURSO_NO_ENCONTRADO")
    void entrenadorInexistente_devuelve404() throws Exception {
        when(entrenadorService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Entrenador", 99L));
        when(entrenadorService.actualizar(eq(99L), any(EntrenadorRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Entrenador", 99L));
        doThrow(new RecursoNoEncontradoException("Entrenador", 99L)).when(entrenadorService).eliminar(99L);

        enviar(HttpMethod.GET, RUTA + "/99", null, user())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        enviar(HttpMethod.PUT, RUTA + "/99", CUERPO_VALIDO, admin()).andExpect(status().isNotFound());
        enviar(HttpMethod.DELETE, RUTA + "/99", null, admin()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE de un entrenador asignado a un equipo: 409 con el código del conflicto")
    void eliminar_conEquipo_devuelve409() throws Exception {
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "El entrenador dirige a un equipo"))
                .when(entrenadorService).eliminar(1L);

        enviar(HttpMethod.DELETE, RUTA + "/1", null, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("GET /entrenadores devuelve el entrenador con todos sus campos")
    void listar_devuelveLosCamposDelEntrenador() throws Exception {
        enviar(HttpMethod.GET, RUTA, null, user())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Alexandre Guimarães"))
                .andExpect(jsonPath("$.contenido[0].nacionalidad").value("Brasil"));
    }
}

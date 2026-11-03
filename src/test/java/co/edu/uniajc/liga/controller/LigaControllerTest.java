package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.LigaService;
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

import java.time.LocalDate;
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
 * {@code LigaController} con {@code @WebMvcTest}: matriz de roles heredada de {@link MatrizRolesCatalogo} más la
 * validación del cuerpo (nombre, temporada y fechas obligatorios) y los errores 404, 409 (LIGA_DUPLICADA) y 422
 * (LIGA_FECHAS_INVALIDAS).
 */
@WebMvcTest(LigaController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class LigaControllerTest extends MatrizRolesCatalogo {

    private static final String RUTA = "/api/v1/ligas";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Liga BetPlay", "temporada": "2026-I", "fechaInicio": "2026-02-01", "fechaFin": "2026-06-30"}""";
    private static final LigaResponse LIGA = new LigaResponse(1L, "Liga BetPlay", "2026-I",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30));

    @MockitoBean
    private LigaService ligaService;

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
        when(ligaService.listar(any())).thenReturn(new PaginaResponse<>(List.of(LIGA), 0, 20, 1, 1));
        when(ligaService.obtener(1L)).thenReturn(LIGA);
        when(ligaService.crear(any(LigaRequest.class))).thenReturn(LIGA);
        when(ligaService.actualizar(eq(1L), any(LigaRequest.class))).thenReturn(LIGA);
    }

    @Override
    protected void verificarServicioSinUso() {
        verifyNoInteractions(ligaService);
    }

    @Test
    @DisplayName("Una liga válida: el servicio recibe las fechas como LocalDate y la respuesta las devuelve en ISO-8601")
    void crear_pasaLasFechasYLasDevuelve() throws Exception {
        enviar(HttpMethod.POST, RUTA, CUERPO_VALIDO, admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Liga BetPlay"))
                .andExpect(jsonPath("$.temporada").value("2026-I"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-02-01"))
                .andExpect(jsonPath("$.fechaFin").value("2026-06-30"));

        verify(ligaService).crear(new LigaRequest("Liga BetPlay", "2026-I",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)));
    }

    static Stream<Arguments> cuerposInvalidos() {
        return Stream.of(
                Arguments.of("nombre vacío", """
                        {"nombre": "", "temporada": "2026-I", "fechaInicio": "2026-02-01", "fechaFin": "2026-06-30"}""",
                        "nombre"),
                Arguments.of("temporada ausente", """
                        {"nombre": "Liga", "fechaInicio": "2026-02-01", "fechaFin": "2026-06-30"}""", "temporada"),
                Arguments.of("temporada solo espacios", """
                        {"nombre": "Liga", "temporada": "  ", "fechaInicio": "2026-02-01", "fechaFin": "2026-06-30"}""",
                        "temporada"),
                Arguments.of("temporada de más de 20 caracteres", """
                        {"nombre": "Liga", "temporada": "%s", "fechaInicio": "2026-02-01", "fechaFin": "2026-06-30"}"""
                        .formatted("x".repeat(21)), "temporada"),
                Arguments.of("fechaInicio ausente", """
                        {"nombre": "Liga", "temporada": "2026-I", "fechaFin": "2026-06-30"}""", "fechaInicio"),
                Arguments.of("fechaFin ausente", """
                        {"nombre": "Liga", "temporada": "2026-I", "fechaInicio": "2026-02-01"}""", "fechaFin"));
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

        verifyNoInteractions(ligaService);
    }

    @Test
    @DisplayName("Una fecha con formato incorrecto (no ISO-8601): 400 CUERPO_INVALIDO")
    void fechaConFormatoIncorrecto_devuelve400() throws Exception {
        enviar(HttpMethod.POST, RUTA, """
                {"nombre": "Liga", "temporada": "2026-I", "fechaInicio": "01/02/2026", "fechaFin": "2026-06-30"}""",
                admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(ligaService);
    }

    @Test
    @DisplayName("Fechas incoherentes: el servicio lanza LIGA_FECHAS_INVALIDAS y el controlador responde 422")
    void fechasInvalidas_devuelve422() throws Exception {
        when(ligaService.crear(any(LigaRequest.class)))
                .thenThrow(new ReglaNegocioException("LIGA_FECHAS_INVALIDAS", "La fecha de fin debe ser posterior."));
        when(ligaService.actualizar(eq(1L), any(LigaRequest.class)))
                .thenThrow(new ReglaNegocioException("LIGA_FECHAS_INVALIDAS", "La fecha de fin debe ser posterior."));

        enviar(HttpMethod.POST, RUTA, CUERPO_VALIDO, admin())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.codigo").value("LIGA_FECHAS_INVALIDAS"));
        enviar(HttpMethod.PUT, RUTA + "/1", CUERPO_VALIDO, admin())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("LIGA_FECHAS_INVALIDAS"));
    }

    @Test
    @DisplayName("Liga repetida (nombre y temporada): POST y PUT responden 409 LIGA_DUPLICADA")
    void ligaDuplicada_devuelve409() throws Exception {
        when(ligaService.crear(any(LigaRequest.class)))
                .thenThrow(new ConflictoException("LIGA_DUPLICADA", "Ya existe esa liga."));
        when(ligaService.actualizar(eq(1L), any(LigaRequest.class)))
                .thenThrow(new ConflictoException("LIGA_DUPLICADA", "Ya existe esa liga."));

        enviar(HttpMethod.POST, RUTA, CUERPO_VALIDO, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIGA_DUPLICADA"));
        enviar(HttpMethod.PUT, RUTA + "/1", CUERPO_VALIDO, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIGA_DUPLICADA"));
    }

    @Test
    @DisplayName("Una liga inexistente: GET, PUT y DELETE responden 404 RECURSO_NO_ENCONTRADO")
    void ligaInexistente_devuelve404() throws Exception {
        when(ligaService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Liga", 99L));
        when(ligaService.actualizar(eq(99L), any(LigaRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Liga", 99L));
        doThrow(new RecursoNoEncontradoException("Liga", 99L)).when(ligaService).eliminar(99L);

        enviar(HttpMethod.GET, RUTA + "/99", null, user())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        enviar(HttpMethod.PUT, RUTA + "/99", CUERPO_VALIDO, admin()).andExpect(status().isNotFound());
        enviar(HttpMethod.DELETE, RUTA + "/99", null, admin()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE de una liga con equipos o partidos: 409 con el código del conflicto")
    void eliminar_conDependientes_devuelve409() throws Exception {
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "La liga tiene equipos"))
                .when(ligaService).eliminar(1L);

        enviar(HttpMethod.DELETE, RUTA + "/1", null, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));
    }
}

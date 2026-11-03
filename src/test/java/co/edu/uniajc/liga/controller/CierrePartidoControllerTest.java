package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.CierrePartidoService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de {@link CierrePartidoController} con {@code @WebMvcTest}: sin base de datos ni Docker. El
 * servicio se simula; la seguridad es la real. Matriz: sin token 401, {@code USER} 403, {@code ADMIN} 200;
 * cuerpo inválido 400; RN-11 y RN-12 salen con su código.
 */
@WebMvcTest(CierrePartidoController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class CierrePartidoControllerTest {

    private static final String RUTA = "/api/v1/partidos/5/finalizar";
    private static final String CUERPO_VALIDO = "{\"golesLocal\": 2, \"golesVisitante\": 1}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CierrePartidoService cierrePartidoService;

    private String admin() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String usuario() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    @Test
    @DisplayName("POST finalizar sin token: 401 y no llega al servicio")
    void finalizar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(cierrePartidoService);
    }

    @Test
    @DisplayName("POST finalizar con rol USER: 403 y no llega al servicio")
    void finalizar_conUser_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", usuario())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verifyNoInteractions(cierrePartidoService);
    }

    @Test
    @DisplayName("POST finalizar con ADMIN: 200 con el resultado (partido, liga, marcador y estado FINALIZADO)")
    void finalizar_conAdmin_devuelve200() throws Exception {
        when(cierrePartidoService.finalizar(eq(5L), any(FinalizarPartidoRequest.class)))
                .thenReturn(new ResultadoPartidoResponse(5L, 10L, 2, 1, EstadoPartido.FINALIZADO));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidoId").value(5))
                .andExpect(jsonPath("$.ligaId").value(10))
                .andExpect(jsonPath("$.golesLocal").value(2))
                .andExpect(jsonPath("$.golesVisitante").value(1))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));

        verify(cierrePartidoService).finalizar(5L, new FinalizarPartidoRequest(2, 1));
    }

    @ParameterizedTest(name = "cuerpo inválido: {0}")
    @ValueSource(strings = {
            "{\"golesVisitante\": 1}",
            "{\"golesLocal\": 2}",
            "{\"golesLocal\": -1, \"golesVisitante\": 1}",
            "{\"golesLocal\": 2, \"golesVisitante\": -3}",
            "{}"})
    @DisplayName("POST finalizar con marcador ausente o negativo: 400 VALIDACION")
    void finalizar_cuerpoInvalido_devuelve400(String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(cierrePartidoService);
    }

    @Test
    @DisplayName("POST finalizar: RN-11 del servicio sale como 422 con su código")
    void finalizar_rn11_devuelve422() throws Exception {
        when(cierrePartidoService.finalizar(eq(5L), any(FinalizarPartidoRequest.class)))
                .thenThrow(new ReglaNegocioException("RN-11", "El marcador no coincide con los goles"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-11"));
    }

    @Test
    @DisplayName("POST finalizar: RN-12 del servicio (ya finalizado) sale como 409 con su código")
    void finalizar_rn12_devuelve409() throws Exception {
        when(cierrePartidoService.finalizar(eq(5L), any(FinalizarPartidoRequest.class)))
                .thenThrow(new ConflictoException("RN-12", "El partido ya está finalizado"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }

    @Test
    @DisplayName("POST finalizar de un partido inexistente: 404")
    void finalizar_partidoInexistente_devuelve404() throws Exception {
        when(cierrePartidoService.finalizar(eq(5L), any(FinalizarPartidoRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Partido", 5L));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La ruta de finalizar solo acepta POST: un GET autenticado da 405")
    void finalizar_conGet_devuelve405() throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", admin()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(cierrePartidoService);
    }
}

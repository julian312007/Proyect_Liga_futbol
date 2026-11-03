package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.GolService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de {@link GolController} con {@code @WebMvcTest}: sin base de datos ni Docker. El servicio se
 * simula; la seguridad es la real (JWT propio, handlers 401/403). Matriz por endpoint (D-2): sin token 401,
 * {@code USER} en escritura 403, {@code ADMIN} 2xx, {@code USER} y {@code ADMIN} en {@code GET} 200; cuerpo
 * inválido 400; los errores de reglas salen con su código.
 */
@WebMvcTest(GolController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class GolControllerTest {

    private static final String RUTA = "/api/v1/partidos/5/goles";
    private static final String CUERPO_VALIDO = "{\"jugadorId\": 101, \"minuto\": 10, \"tipo\": \"NORMAL\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GolService golService;

    private String admin() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String usuario() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    private static GolResponse golCreado() {
        return new GolResponse(77L, 5L, new ResumenResponse(101L, "Luis"), new ResumenResponse(1L, "Local FC"),
                10, TipoGol.NORMAL);
    }

    // ================================================================ POST

    @Test
    @DisplayName("POST sin token: 401 y no llega al servicio")
    void post_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("POST con rol USER: 403 y no llega al servicio")
    void post_conUser_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", usuario())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("POST con ADMIN: 201, cabecera Location con el id del gol y el gol en el cuerpo")
    void post_conAdmin_devuelve201ConLocation() throws Exception {
        when(golService.registrar(eq(5L), any(GolRequest.class))).thenReturn(golCreado());

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/partidos/5/goles/77")))
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.partidoId").value(5))
                .andExpect(jsonPath("$.jugador.id").value(101))
                .andExpect(jsonPath("$.jugador.nombre").value("Luis"))
                .andExpect(jsonPath("$.equipo.id").value(1))
                .andExpect(jsonPath("$.minuto").value(10))
                .andExpect(jsonPath("$.tipo").value("NORMAL"));

        verify(golService).registrar(5L, new GolRequest(101L, 10, TipoGol.NORMAL));
    }

    @ParameterizedTest(name = "cuerpo inválido: {0}")
    @ValueSource(strings = {
            "{\"minuto\": 10, \"tipo\": \"NORMAL\"}",
            "{\"jugadorId\": 101, \"tipo\": \"NORMAL\"}",
            "{\"jugadorId\": 101, \"minuto\": 10}",
            "{\"jugadorId\": 101, \"minuto\": 0, \"tipo\": \"NORMAL\"}",
            "{\"jugadorId\": 101, \"minuto\": 131, \"tipo\": \"NORMAL\"}",
            "{\"jugadorId\": -1, \"minuto\": 10, \"tipo\": \"NORMAL\"}",
            "{}"})
    @DisplayName("POST con cuerpo inválido (falta un campo, minuto fuera de 1-130, jugador negativo): 400 VALIDACION")
    void post_cuerpoInvalido_devuelve400(String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("POST con minuto 131: el detalle señala el campo 'minuto'")
    void post_minutoFueraDeRango_indicaElCampo() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jugadorId\": 101, \"minuto\": 131, \"tipo\": \"NORMAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("minuto:"))));
    }

    @Test
    @DisplayName("POST con un tipo de gol desconocido o JSON mal formado: 400 CUERPO_INVALIDO")
    void post_tipoDesconocidoOJsonRoto_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jugadorId\": 101, \"minuto\": 10, \"tipo\": \"VOLEA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jugadorId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("POST con un id de partido que no es número: 400 PARAMETRO_INVALIDO")
    void post_partidoIdNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/abc/goles").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("POST: RN-08 del servicio sale como 422 con su código")
    void post_rn08_devuelve422() throws Exception {
        when(golService.registrar(eq(5L), any(GolRequest.class)))
                .thenThrow(new ReglaNegocioException("RN-08", "El jugador no pertenece al partido"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-08"));
    }

    @Test
    @DisplayName("POST: RN-12 del servicio (partido finalizado) sale como 409 con su código")
    void post_rn12_devuelve409() throws Exception {
        when(golService.registrar(eq(5L), any(GolRequest.class)))
                .thenThrow(new ConflictoException("RN-12", "El partido está finalizado"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }

    @Test
    @DisplayName("POST: un partido inexistente sale como 404")
    void post_partidoInexistente_devuelve404() throws Exception {
        when(golService.registrar(eq(5L), any(GolRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Partido", 5L));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ================================================================ GET

    @Test
    @DisplayName("GET sin token: 401")
    void get_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

        verifyNoInteractions(golService);
    }

    @ParameterizedTest(name = "GET con rol {0}: 200")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET con USER o ADMIN: 200 con la página de goles")
    void get_conCualquierRol_devuelve200(String rol) throws Exception {
        when(golService.listar(eq(5L), any(Pageable.class))).thenReturn(
                new PaginaResponse<>(List.of(golCreado()), 0, 20, 1, 1));
        String token = "USER".equals(rol) ? usuario() : admin();

        mockMvc.perform(get(RUTA).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(77))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.pagina").value(0));
    }

    @Test
    @DisplayName("GET pasa al servicio la página, el tamaño y el orden pedidos")
    void get_pasaElPageableAlServicio() throws Exception {
        when(golService.listar(eq(5L), any(Pageable.class))).thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(RUTA + "?page=1&size=5&sort=minuto,desc").header("Authorization", usuario()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(golService).listar(eq(5L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().getOrderFor("minuto")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("minuto").isDescending()).isTrue();
    }

    @Test
    @DisplayName("GET de un partido inexistente: 404")
    void get_partidoInexistente_devuelve404() throws Exception {
        when(golService.listar(eq(5L), any(Pageable.class))).thenThrow(new RecursoNoEncontradoException("Partido", 5L));

        mockMvc.perform(get(RUTA).header("Authorization", usuario()))
                .andExpect(status().isNotFound());
    }

    // ================================================================ DELETE

    @Test
    @DisplayName("DELETE sin token: 401")
    void delete_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete(RUTA + "/77")).andExpect(status().isUnauthorized());

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("DELETE con rol USER: 403")
    void delete_conUser_devuelve403() throws Exception {
        mockMvc.perform(delete(RUTA + "/77").header("Authorization", usuario()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(golService);
    }

    @Test
    @DisplayName("DELETE con ADMIN: 204 sin cuerpo y delega con el partido y el gol")
    void delete_conAdmin_devuelve204() throws Exception {
        mockMvc.perform(delete(RUTA + "/77").header("Authorization", admin()))
                .andExpect(status().isNoContent());

        verify(golService).eliminar(5L, 77L);
    }

    @Test
    @DisplayName("DELETE: RN-12 del servicio (partido finalizado) sale como 409 con su código")
    void delete_rn12_devuelve409() throws Exception {
        doThrow(new ConflictoException("RN-12", "El partido está finalizado")).when(golService).eliminar(5L, 77L);

        mockMvc.perform(delete(RUTA + "/77").header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }
}

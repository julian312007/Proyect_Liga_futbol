package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.TarjetaService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
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
 * Prueba de {@link TarjetaController} con {@code @WebMvcTest}: sin base de datos ni Docker. El servicio se
 * simula; la seguridad es la real. Matriz por endpoint: sin token 401, {@code USER} en escritura 403,
 * {@code ADMIN} 2xx, {@code USER} y {@code ADMIN} en {@code GET} 200; cuerpo inválido 400.
 */
@WebMvcTest(TarjetaController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class TarjetaControllerTest {

    private static final String RUTA = "/api/v1/partidos/5/tarjetas";
    private static final String CUERPO_VALIDO = "{\"jugadorId\": 101, \"tipo\": \"AMARILLA\", \"minuto\": 40}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TarjetaService tarjetaService;

    private String admin() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String usuario() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    private static TarjetaResponse tarjetaCreada() {
        return new TarjetaResponse(88L, 5L, new ResumenResponse(101L, "Luis"), new ResumenResponse(1L, "Local FC"),
                TipoTarjeta.AMARILLA, 40);
    }

    // ================================================================ POST

    @Test
    @DisplayName("POST sin token: 401 y no llega al servicio")
    void post_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("POST con rol USER: 403 y no llega al servicio")
    void post_conUser_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", usuario())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("POST con ADMIN: 201, cabecera Location con el id de la tarjeta y la tarjeta en el cuerpo")
    void post_conAdmin_devuelve201ConLocation() throws Exception {
        when(tarjetaService.registrar(eq(5L), any(TarjetaRequest.class))).thenReturn(tarjetaCreada());

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/partidos/5/tarjetas/88")))
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.partidoId").value(5))
                .andExpect(jsonPath("$.jugador.nombre").value("Luis"))
                .andExpect(jsonPath("$.tipo").value("AMARILLA"))
                .andExpect(jsonPath("$.minuto").value(40));

        verify(tarjetaService).registrar(5L, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 40));
    }

    @ParameterizedTest(name = "cuerpo inválido: {0}")
    @ValueSource(strings = {
            "{\"tipo\": \"AMARILLA\", \"minuto\": 40}",
            "{\"jugadorId\": 101, \"minuto\": 40}",
            "{\"jugadorId\": 101, \"tipo\": \"AMARILLA\"}",
            "{\"jugadorId\": 101, \"tipo\": \"AMARILLA\", \"minuto\": 0}",
            "{\"jugadorId\": 101, \"tipo\": \"AMARILLA\", \"minuto\": 131}",
            "{\"jugadorId\": 0, \"tipo\": \"AMARILLA\", \"minuto\": 40}",
            "{}"})
    @DisplayName("POST con cuerpo inválido (falta un campo, minuto fuera de 1-130, jugador no positivo): 400 VALIDACION")
    void post_cuerpoInvalido_devuelve400(String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("POST con un tipo de tarjeta desconocido: 400 CUERPO_INVALIDO")
    void post_tipoDesconocido_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jugadorId\": 101, \"tipo\": \"AZUL\", \"minuto\": 40}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("POST: TARJETA_JUGADOR_EXPULSADO del servicio sale como 422 con su código")
    void post_expulsado_devuelve422() throws Exception {
        when(tarjetaService.registrar(eq(5L), any(TarjetaRequest.class)))
                .thenThrow(new ReglaNegocioException("TARJETA_JUGADOR_EXPULSADO", "El jugador ya fue expulsado"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("TARJETA_JUGADOR_EXPULSADO"));
    }

    @Test
    @DisplayName("POST: PARTIDO_NO_EN_JUEGO del servicio sale como 409 con su código")
    void post_partidoNoEnJuego_devuelve409() throws Exception {
        when(tarjetaService.registrar(eq(5L), any(TarjetaRequest.class)))
                .thenThrow(new ConflictoException("PARTIDO_NO_EN_JUEGO", "El partido no está en juego"));

        mockMvc.perform(post(RUTA).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_NO_EN_JUEGO"));
    }

    // ================================================================ GET

    @Test
    @DisplayName("GET sin token: 401")
    void get_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

        verifyNoInteractions(tarjetaService);
    }

    @ParameterizedTest(name = "GET con rol {0}: 200")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET con USER o ADMIN: 200 con la página de tarjetas")
    void get_conCualquierRol_devuelve200(String rol) throws Exception {
        when(tarjetaService.listar(eq(5L), any(Pageable.class))).thenReturn(
                new PaginaResponse<>(List.of(tarjetaCreada()), 0, 20, 1, 1));
        String token = "USER".equals(rol) ? usuario() : admin();

        mockMvc.perform(get(RUTA).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(88))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("GET de un partido inexistente: 404")
    void get_partidoInexistente_devuelve404() throws Exception {
        when(tarjetaService.listar(eq(5L), any(Pageable.class)))
                .thenThrow(new RecursoNoEncontradoException("Partido", 5L));

        mockMvc.perform(get(RUTA).header("Authorization", usuario()))
                .andExpect(status().isNotFound());
    }

    // ================================================================ DELETE

    @Test
    @DisplayName("DELETE sin token: 401")
    void delete_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete(RUTA + "/88")).andExpect(status().isUnauthorized());

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("DELETE con rol USER: 403")
    void delete_conUser_devuelve403() throws Exception {
        mockMvc.perform(delete(RUTA + "/88").header("Authorization", usuario()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tarjetaService);
    }

    @Test
    @DisplayName("DELETE con ADMIN: 204 sin cuerpo y delega con el partido y la tarjeta")
    void delete_conAdmin_devuelve204() throws Exception {
        mockMvc.perform(delete(RUTA + "/88").header("Authorization", admin()))
                .andExpect(status().isNoContent());

        verify(tarjetaService).eliminar(5L, 88L);
    }

    @Test
    @DisplayName("DELETE: RN-12 del servicio (partido finalizado) sale como 409 con su código")
    void delete_rn12_devuelve409() throws Exception {
        doThrow(new ConflictoException("RN-12", "El partido está finalizado")).when(tarjetaService).eliminar(5L, 88L);

        mockMvc.perform(delete(RUTA + "/88").header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }
}

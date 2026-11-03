package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.PartidoService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de {@link PartidoController} con {@code @WebMvcTest}: sin base de datos ni Docker. El servicio se
 * simula; la seguridad es la real (cadena de filtros, JWT propio y handlers 401/403) con usuarios en memoria.
 * Cubre la matriz de roles de cada endpoint (401 sin token, 403 con USER en escritura, 2xx con ADMIN, 200 con
 * USER o ADMIN en lectura), la validación del cuerpo (400), el 201 con {@code Location} y que los errores de
 * dominio salgan con su código y su estado.
 *
 * <p>Los nombres de los datos de prueba no llevan tildes: MockMvc puede decodificar mal un JSON sin charset.
 */
@WebMvcTest(PartidoController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class PartidoControllerTest {

    private static final String RUTA = "/api/v1/partidos";
    private static final String CUERPO_VALIDO = """
            {"ligaId": 1, "jornada": 3, "fechaHora": "2026-02-01T20:00:00-05:00",
             "equipoLocalId": 10, "equipoVisitanteId": 11, "estadioId": 20, "arbitroId": 30}
            """;
    private static final PartidoRequest SOLICITUD_VALIDA =
            new PartidoRequest(1L, 3, Instant.parse("2026-02-01T20:00:00-05:00"), 10L, 11L, 20L, 30L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PartidoService partidoService;

    // ======================================================================= GET (listado)

    @Test
    @DisplayName("GET /partidos sin token: 401 con formato ApiError")
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("GET /partidos con USER: 200 y la página tiene la forma PaginaResponse con la fecha en ISO-8601")
    void listar_conUser_devuelve200() throws Exception {
        when(partidoService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(respuesta()), 0, 20, 1, 1));

        mockMvc.perform(get(RUTA).header("Authorization", tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(7))
                .andExpect(jsonPath("$.contenido[0].liga.nombre").value("Liga Betplay"))
                .andExpect(jsonPath("$.contenido[0].equipoLocal.id").value(10))
                .andExpect(jsonPath("$.contenido[0].equipoVisitante.nombre").value("Medellin FC"))
                .andExpect(jsonPath("$.contenido[0].estadio.nombre").value("Pascual Guerrero"))
                .andExpect(jsonPath("$.contenido[0].arbitro.id").value(30))
                .andExpect(jsonPath("$.contenido[0].estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.contenido[0].golesLocal").value(0))
                .andExpect(jsonPath("$.contenido[0].fechaHora").value("2026-02-02T01:00:00Z"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    @DisplayName("GET /partidos con ADMIN: 200")
    void listar_conAdmin_devuelve200() throws Exception {
        when(partidoService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(RUTA).header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());
    }

    @Test
    @DisplayName("GET /partidos pasa al servicio los cuatro filtros del calendario (F8)")
    void listar_conFiltros_losPasaAlServicio() throws Exception {
        when(partidoService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(RUTA)
                        .param("ligaId", "1").param("jornada", "3").param("equipoId", "10").param("estadioId", "20")
                        .header("Authorization", tokenUser()))
                .andExpect(status().isOk());

        verify(partidoService).listar(eq(1L), eq(3), eq(10L), eq(20L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /partidos sin filtros: el servicio recibe todos nulos (los filtros son opcionales)")
    void listar_sinFiltros_losPasaNulos() throws Exception {
        when(partidoService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(RUTA).header("Authorization", tokenUser())).andExpect(status().isOk());

        verify(partidoService).listar(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /partidos con page, size y sort: el servicio recibe el Pageable pedido")
    void listar_conPaginacion_laPasaAlServicio() throws Exception {
        when(partidoService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get(RUTA).param("page", "2").param("size", "5").param("sort", "fechaHora,desc")
                        .header("Authorization", tokenUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoService).listar(isNull(), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().getOrderFor("fechaHora")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("fechaHora").isDescending()).isTrue();
    }

    @Test
    @DisplayName("GET /partidos con un filtro de formato incorrecto (ligaId=abc): 400 PARAMETRO_INVALIDO")
    void listar_filtroConFormatoIncorrecto_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("ligaId", "abc").header("Authorization", tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(partidoService);
    }

    // ================================================================================ GET /{id}

    @Test
    @DisplayName("GET /partidos/{id} sin token: 401")
    void obtener_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/7")).andExpect(status().isUnauthorized());

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("GET /partidos/{id} con USER y con ADMIN: 200")
    void obtener_conUserYAdmin_devuelve200() throws Exception {
        when(partidoService.obtener(7L)).thenReturn(respuesta());

        mockMvc.perform(get(RUTA + "/7").header("Authorization", tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(get(RUTA + "/7").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /partidos/{id} de un partido que no existe: 404 con su código")
    void obtener_inexistente_devuelve404() throws Exception {
        when(partidoService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Partido", 99L));

        mockMvc.perform(get(RUTA + "/99").header("Authorization", tokenUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("GET /partidos/{id} con un id que no es numérico: 400 PARAMETRO_INVALIDO")
    void obtener_idNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA + "/abc").header("Authorization", tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ======================================================= Escritura: matriz 401 / 403 / 2xx

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST,   /api/v1/partidos",
            "PUT,    /api/v1/partidos/7",
            "DELETE, /api/v1/partidos/7",
            "POST,   /api/v1/partidos/7/iniciar"
    })
    @DisplayName("Escritura y acciones sin token: 401 y no llegan al servicio")
    void escritura_sinToken_devuelve401(String metodo, String ruta) throws Exception {
        mockMvc.perform(peticion(metodo, ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(partidoService);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST,   /api/v1/partidos",
            "PUT,    /api/v1/partidos/7",
            "DELETE, /api/v1/partidos/7",
            "POST,   /api/v1/partidos/7/iniciar"
    })
    @DisplayName("Escritura y acciones con USER: 403 ACCESO_DENEGADO y no llegan al servicio")
    void escritura_conUser_devuelve403(String metodo, String ruta) throws Exception {
        mockMvc.perform(peticion(metodo, ruta).header("Authorization", tokenUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verifyNoInteractions(partidoService);
    }

    // ===================================================================================== POST

    @Test
    @DisplayName("POST /partidos con ADMIN: 201, cabecera Location hacia el nuevo partido y el cuerpo creado")
    void crear_conAdmin_devuelve201ConLocation() throws Exception {
        when(partidoService.crear(any(PartidoRequest.class))).thenReturn(respuesta());

        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/partidos/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"));

        verify(partidoService).crear(SOLICITUD_VALIDA);
    }

    @Test
    @DisplayName("POST /partidos ignora id, marcador y estado enviados por el cliente: el servicio solo recibe la solicitud")
    void crear_ignoraCamposQueElClienteNoFija() throws Exception {
        when(partidoService.crear(any(PartidoRequest.class))).thenReturn(respuesta());

        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 999, "estado": "FINALIZADO", "golesLocal": 5, "golesVisitante": 4,
                                 "ligaId": 1, "jornada": 3, "fechaHora": "2026-02-01T20:00:00-05:00",
                                 "equipoLocalId": 10, "equipoVisitanteId": 11, "estadioId": 20, "arbitroId": 30}
                                """))
                .andExpect(status().isCreated());

        verify(partidoService).crear(SOLICITUD_VALIDA);
    }

    @Test
    @DisplayName("POST /partidos con el cuerpo vacío: 400 VALIDACION con un detalle por cada campo obligatorio")
    void crear_cuerpoVacio_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("ligaId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("jornada:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("fechaHora:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("equipoLocalId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("equipoVisitanteId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("estadioId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("arbitroId:"))));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("POST /partidos con jornada 0: 400 (la jornada empieza en 1)")
    void crear_jornadaCero_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("\"jornada\": 3", "\"jornada\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("jornada:"))));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("POST /partidos con JSON mal formado: 400 CUERPO_INVALIDO")
    void crear_jsonMalFormado_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ligaId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("POST /partidos con una fecha que no es ISO-8601: 400 CUERPO_INVALIDO")
    void crear_fechaInvalida_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("2026-02-01T20:00:00-05:00", "manana por la noche")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("POST /partidos cuando el servicio rechaza RN-04: 422 con el código de la regla")
    void crear_rn04_devuelve422() throws Exception {
        when(partidoService.crear(any(PartidoRequest.class)))
                .thenThrow(new ReglaNegocioException("RN-04", "Un equipo no puede enfrentarse a si mismo."));

        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-04"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"RN-05", "RN-06", "RN-07"})
    @DisplayName("POST /partidos cuando el servicio detecta un choque (RN-05, RN-06, RN-07): 409 con el código de la regla")
    void crear_choque_devuelve409(String codigo) throws Exception {
        when(partidoService.crear(any(PartidoRequest.class))).thenThrow(new ConflictoException(codigo, "Choque."));

        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(codigo));
    }

    @Test
    @DisplayName("POST /partidos con una liga o equipo inexistente: 404")
    void crear_relacionInexistente_devuelve404() throws Exception {
        when(partidoService.crear(any(PartidoRequest.class))).thenThrow(new RecursoNoEncontradoException("Equipo", 10L));

        mockMvc.perform(post(RUTA)
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    // ====================================================================================== PUT

    @Test
    @DisplayName("PUT /partidos/{id} con ADMIN: 200 con el partido actualizado")
    void actualizar_conAdmin_devuelve200() throws Exception {
        when(partidoService.actualizar(eq(7L), any(PartidoRequest.class))).thenReturn(respuesta());

        mockMvc.perform(put(RUTA + "/7")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(partidoService).actualizar(7L, SOLICITUD_VALIDA);
    }

    @Test
    @DisplayName("PUT /partidos/{id} con el cuerpo inválido: 400")
    void actualizar_cuerpoInvalido_devuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/7")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(partidoService);
    }

    @Test
    @DisplayName("PUT /partidos/{id} de un partido finalizado (RN-12): 409 con el código de la regla")
    void actualizar_partidoFinalizado_devuelve409() throws Exception {
        when(partidoService.actualizar(eq(7L), any(PartidoRequest.class)))
                .thenThrow(new ConflictoException("RN-12", "Un partido finalizado no puede modificarse."));

        mockMvc.perform(put(RUTA + "/7")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }

    @Test
    @DisplayName("PUT /partidos/{id} de un partido que no existe: 404")
    void actualizar_inexistente_devuelve404() throws Exception {
        when(partidoService.actualizar(eq(99L), any(PartidoRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Partido", 99L));

        mockMvc.perform(put(RUTA + "/99")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    // =================================================================================== DELETE

    @Test
    @DisplayName("DELETE /partidos/{id} con ADMIN: 204 sin cuerpo")
    void eliminar_conAdmin_devuelve204() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete(RUTA + "/7").header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());

        verify(partidoService).eliminar(7L);
    }

    @Test
    @DisplayName("DELETE /partidos/{id} de un partido finalizado (RN-12): 409 con el código de la regla")
    void eliminar_partidoFinalizado_devuelve409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictoException("RN-12", "Un partido finalizado no puede eliminarse."))
                .when(partidoService).eliminar(7L);

        mockMvc.perform(MockMvcRequestBuilders.delete(RUTA + "/7").header("Authorization", tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
    }

    // =================================================================================== iniciar

    @Test
    @DisplayName("POST /partidos/{id}/iniciar con ADMIN: 200 con el partido EN_JUEGO")
    void iniciar_conAdmin_devuelve200() throws Exception {
        when(partidoService.iniciar(7L)).thenReturn(respuestaConEstado(EstadoPartido.EN_JUEGO));

        mockMvc.perform(post(RUTA + "/7/iniciar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_JUEGO"));
    }

    @Test
    @DisplayName("POST /partidos/{id}/iniciar de un partido que no está PROGRAMADO: 409 PARTIDO_ESTADO_INVALIDO")
    void iniciar_estadoInvalido_devuelve409() throws Exception {
        when(partidoService.iniciar(7L)).thenThrow(new ConflictoException("PARTIDO_ESTADO_INVALIDO", "No esta programado."));

        mockMvc.perform(post(RUTA + "/7/iniciar").header("Authorization", tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_ESTADO_INVALIDO"));
    }

    // ===================================================================================== Apoyo

    private MockHttpServletRequestBuilder peticion(String metodo, String ruta) {
        return MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_VALIDO);
    }

    private String tokenAdmin() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String tokenUser() {
        return "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    private static PartidoResponse respuesta() {
        return respuestaConEstado(EstadoPartido.PROGRAMADO);
    }

    private static PartidoResponse respuestaConEstado(EstadoPartido estado) {
        return new PartidoResponse(7L,
                new ResumenResponse(1L, "Liga Betplay"),
                3,
                Instant.parse("2026-02-01T20:00:00-05:00"),
                new ResumenResponse(10L, "Cali FC"),
                new ResumenResponse(11L, "Medellin FC"),
                new ResumenResponse(20L, "Pascual Guerrero"),
                new ResumenResponse(30L, "Arbitro Uno"),
                0, 0, estado);
    }
}

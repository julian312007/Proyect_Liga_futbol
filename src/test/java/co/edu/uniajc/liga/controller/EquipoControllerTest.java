package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.PosicionJugador;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.EquipoService;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de {@link EquipoController} con {@code @WebMvcTest}: sin base de datos ni Docker. El servicio se
 * simula; la seguridad es la real (filtro JWT, {@code SecurityConfig}, {@code @PreAuthorize} y handlers 401/403).
 * Matriz por endpoint: sin token 401, {@code USER} en escritura 403, {@code ADMIN} 2xx, {@code USER} y
 * {@code ADMIN} en lectura 200, cuerpo inválido 400 y errores de dominio con su {@code codigo}.
 *
 * <p>Los textos de las respuestas son ASCII a propósito: MockMvc decodifica el JSON sin charset como
 * ISO-8859-1 y las tildes se verían alteradas.
 */
@WebMvcTest(EquipoController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class EquipoControllerTest {

    private static final String RUTA = "/api/v1/equipos";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Deportivo Cali", "ciudad": "Cali", "anioFundacion": 1912,
             "estadioLocalId": 2, "entrenadorId": 3, "ligaId": 1}
            """;
    private static final EquipoRequest PETICION_VALIDA = new EquipoRequest("Deportivo Cali", "Cali", 1912, 2L, 3L, 1L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EquipoService equipoService;

    // ================================================================ GET /equipos

    @Test
    @DisplayName("GET /equipos sin token: 401 con codigo NO_AUTENTICADO y sin llamar al servicio")
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(equipoService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /equipos con USER o ADMIN: 200 con la página en la forma estable de PaginaResponse")
    void listar_conCualquierRol_devuelve200(String rol) throws Exception {
        when(equipoService.listar(any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(equipoResponse()), 0, 20, 1, 1));

        mockMvc.perform(get(RUTA).header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(10))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Deportivo Cali"))
                .andExpect(jsonPath("$.contenido[0].liga.nombre").value("Liga BetPlay"))
                .andExpect(jsonPath("$.contenido[0].estadioLocal.id").value(2))
                .andExpect(jsonPath("$.contenido[0].entrenador.id").value(3))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    @DisplayName("GET /equipos?page=&size=&sort= pasa el Pageable del cliente al servicio")
    void listar_conParametrosDePaginacion_losPasaAlServicio() throws Exception {
        when(equipoService.listar(any(Pageable.class))).thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(RUTA).param("page", "1").param("size", "5").param("sort", "nombre,desc")
                        .header("Authorization", autorizacion("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(equipoService).listar(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "nombre"));
    }

    // ============================================================ GET /equipos/{id}

    @Test
    @DisplayName("GET /equipos/{id} sin token: 401")
    void obtener_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/10")).andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /equipos/{id} con USER o ADMIN: 200 con el equipo")
    void obtener_conCualquierRol_devuelve200(String rol) throws Exception {
        when(equipoService.obtener(10L)).thenReturn(equipoResponse());

        mockMvc.perform(get(RUTA + "/10").header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.anioFundacion").value(1912));
    }

    @Test
    @DisplayName("GET /equipos/{id} de un equipo que no existe: 404 con codigo RECURSO_NO_ENCONTRADO")
    void obtener_inexistente_devuelve404() throws Exception {
        when(equipoService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(get(RUTA + "/99").header("Authorization", autorizacion("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("GET /equipos/abc (id no numérico): 400 PARAMETRO_INVALIDO")
    void obtener_idNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA + "/abc").header("Authorization", autorizacion("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(equipoService);
    }

    // ====================================================== GET /equipos/{id}/plantel

    @Test
    @DisplayName("GET /equipos/{id}/plantel sin token: 401")
    void plantel_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/10/plantel")).andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /equipos/{id}/plantel con USER o ADMIN: 200 con la página de jugadores")
    void plantel_conCualquierRol_devuelve200(String rol) throws Exception {
        JugadorResponse jugador = new JugadorResponse(100L, "Juan Perez", LocalDate.of(2000, 5, 17),
                PosicionJugador.DELANTERO, 9, new ResumenResponse(10L, "Deportivo Cali"));
        when(equipoService.plantel(eq(10L), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(jugador), 0, 20, 1, 1));

        mockMvc.perform(get(RUTA + "/10/plantel").header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.contenido[0].fechaNacimiento").value("2000-05-17"))
                .andExpect(jsonPath("$.contenido[0].posicion").value("DELANTERO"))
                .andExpect(jsonPath("$.contenido[0].dorsal").value(9))
                .andExpect(jsonPath("$.contenido[0].equipo.id").value(10))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("GET /equipos/{id}/plantel de un equipo que no existe: 404")
    void plantel_equipoInexistente_devuelve404() throws Exception {
        when(equipoService.plantel(eq(99L), any(Pageable.class)))
                .thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(get(RUTA + "/99/plantel").header("Authorization", autorizacion("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ================================================ GET /equipos/{id}/cuerpo-tecnico

    @Test
    @DisplayName("GET /equipos/{id}/cuerpo-tecnico sin token: 401")
    void cuerpoTecnico_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/10/cuerpo-tecnico")).andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /equipos/{id}/cuerpo-tecnico con USER o ADMIN: 200 con el entrenador")
    void cuerpoTecnico_conCualquierRol_devuelve200(String rol) throws Exception {
        when(equipoService.cuerpoTecnico(10L))
                .thenReturn(new CuerpoTecnicoResponse(10L, "Deportivo Cali", 3L, "Alexandre Guimaraes", "Brasil"));

        mockMvc.perform(get(RUTA + "/10/cuerpo-tecnico").header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipoId").value(10))
                .andExpect(jsonPath("$.equipo").value("Deportivo Cali"))
                .andExpect(jsonPath("$.entrenadorId").value(3))
                .andExpect(jsonPath("$.entrenador").value("Alexandre Guimaraes"))
                .andExpect(jsonPath("$.nacionalidad").value("Brasil"));
    }

    @Test
    @DisplayName("GET /equipos/{id}/cuerpo-tecnico de un equipo que no existe: 404")
    void cuerpoTecnico_equipoInexistente_devuelve404() throws Exception {
        when(equipoService.cuerpoTecnico(99L)).thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(get(RUTA + "/99/cuerpo-tecnico").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ================================================================ POST /equipos

    @Test
    @DisplayName("POST /equipos sin token: 401 y sin llamar al servicio")
    void crear_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("POST /equipos con USER: 403 ACCESO_DENEGADO y sin llamar al servicio")
    void crear_conUser_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("POST /equipos con ADMIN: 201, cabecera Location del nuevo equipo y el cuerpo creado")
    void crear_conAdmin_devuelve201ConLocation() throws Exception {
        when(equipoService.crear(PETICION_VALIDA)).thenReturn(equipoResponse());

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/equipos/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.nombre").value("Deportivo Cali"));

        verify(equipoService).crear(PETICION_VALIDA);
    }

    @Test
    @DisplayName("POST /equipos con un campo id en el cuerpo: se ignora, el cliente no fija el id")
    void crear_conIdEnElCuerpo_seIgnora() throws Exception {
        when(equipoService.crear(PETICION_VALIDA)).thenReturn(equipoResponse());

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 999, "nombre": "Deportivo Cali", "ciudad": "Cali", "anioFundacion": 1912,
                                 "estadioLocalId": 2, "entrenadorId": 3, "ligaId": 1}
                                """))
                .andExpect(status().isCreated());

        verify(equipoService).crear(PETICION_VALIDA);
    }

    @Test
    @DisplayName("POST /equipos con campos vacíos o fuera de rango: 400 VALIDACION con el detalle por campo")
    void crear_cuerpoInvalido_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "   ", "ciudad": "", "anioFundacion": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("nombre:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("ciudad:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("anioFundacion:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("estadioLocalId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("entrenadorId:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("ligaId:"))));

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("POST /equipos con un nombre de más de 100 caracteres: 400")
    void crear_nombreDemasiadoLargo_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("Deportivo Cali", "x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("nombre:"))));

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("POST /equipos con JSON mal formado: 400 CUERPO_INVALIDO")
    void crear_jsonMalFormado_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("POST /equipos con el entrenador de otro equipo (RN-03): 409 con codigo RN-03")
    void crear_rn03_entrenadorOcupado_devuelve409() throws Exception {
        when(equipoService.crear(PETICION_VALIDA))
                .thenThrow(new ConflictoException("RN-03", "El entrenador ya dirige a otro equipo."));

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-03"));
    }

    @Test
    @DisplayName("POST /equipos con un año de fundación futuro: 422 con codigo ANIO_FUNDACION_FUTURO")
    void crear_anioFuturo_devuelve422() throws Exception {
        when(equipoService.crear(PETICION_VALIDA))
                .thenThrow(new ReglaNegocioException("ANIO_FUNDACION_FUTURO", "El año es futuro."));

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("ANIO_FUNDACION_FUTURO"));
    }

    // ============================================================= PUT /equipos/{id}

    @Test
    @DisplayName("PUT /equipos/{id} sin token: 401")
    void actualizar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(put(RUTA + "/10").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("PUT /equipos/{id} con USER: 403")
    void actualizar_conUser_devuelve403() throws Exception {
        mockMvc.perform(put(RUTA + "/10").header("Authorization", autorizacion("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("PUT /equipos/{id} con ADMIN: 200 con el equipo actualizado")
    void actualizar_conAdmin_devuelve200() throws Exception {
        when(equipoService.actualizar(10L, PETICION_VALIDA)).thenReturn(equipoResponse());

        mockMvc.perform(put(RUTA + "/10").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(equipoService).actualizar(10L, PETICION_VALIDA);
    }

    @Test
    @DisplayName("PUT /equipos/{id} con cuerpo inválido: 400 y sin llamar al servicio")
    void actualizar_cuerpoInvalido_devuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/10").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("PUT /equipos/{id} de un equipo que no existe: 404")
    void actualizar_inexistente_devuelve404() throws Exception {
        when(equipoService.actualizar(99L, PETICION_VALIDA)).thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(put(RUTA + "/99").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    // ========================================================== DELETE /equipos/{id}

    @Test
    @DisplayName("DELETE /equipos/{id} sin token: 401")
    void eliminar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete(RUTA + "/10")).andExpect(status().isUnauthorized());

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("DELETE /equipos/{id} con USER: 403")
    void eliminar_conUser_devuelve403() throws Exception {
        mockMvc.perform(delete(RUTA + "/10").header("Authorization", autorizacion("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipoService);
    }

    @Test
    @DisplayName("DELETE /equipos/{id} con ADMIN: 204 sin cuerpo")
    void eliminar_conAdmin_devuelve204() throws Exception {
        mockMvc.perform(delete(RUTA + "/10").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNoContent());

        verify(equipoService).eliminar(10L);
    }

    @Test
    @DisplayName("DELETE /equipos/{id} de un equipo con dependientes: 409 CONFLICTO_DEPENDENCIAS")
    void eliminar_conDependientes_devuelve409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "tiene jugadores"))
                .when(equipoService).eliminar(10L);

        mockMvc.perform(delete(RUTA + "/10").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("DELETE /equipos/{id} de un equipo que no existe: 404")
    void eliminar_inexistente_devuelve404() throws Exception {
        org.mockito.Mockito.doThrow(new RecursoNoEncontradoException("Equipo", 99L))
                .when(equipoService).eliminar(99L);

        mockMvc.perform(delete(RUTA + "/99").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ================================================================== Apoyo

    private String autorizacion(String rol) {
        return "ADMIN".equals(rol)
                ? "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN)
                : "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    private static EquipoResponse equipoResponse() {
        return new EquipoResponse(10L, "Deportivo Cali", "Cali", 1912,
                new ResumenResponse(2L, "Pascual Guerrero"),
                new ResumenResponse(3L, "Alexandre Guimaraes"),
                new ResumenResponse(1L, "Liga BetPlay"));
    }
}

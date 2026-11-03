package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.JugadorRequest;
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
import co.edu.uniajc.liga.service.JugadorService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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

/**
 * Prueba de {@link JugadorController} con {@code @WebMvcTest}: sin base de datos ni Docker. El servicio se
 * simula; la seguridad es la real (filtro JWT, {@code SecurityConfig}, {@code @PreAuthorize} y handlers 401/403).
 * Matriz por endpoint: sin token 401, {@code USER} en escritura 403, {@code ADMIN} 2xx, {@code USER} y
 * {@code ADMIN} en lectura 200, cuerpo inválido 400 y errores de dominio con su {@code codigo}.
 *
 * <p>Los textos de las respuestas son ASCII a propósito: MockMvc decodifica el JSON sin charset como
 * ISO-8859-1 y las tildes se verían alteradas.
 */
@WebMvcTest(JugadorController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class JugadorControllerTest {

    private static final String RUTA = "/api/v1/jugadores";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Juan Perez", "fechaNacimiento": "2000-05-17", "posicion": "DELANTERO",
             "dorsal": 9, "equipoId": 10}
            """;
    private static final JugadorRequest PETICION_VALIDA =
            new JugadorRequest("Juan Perez", LocalDate.of(2000, 5, 17), PosicionJugador.DELANTERO, 9, 10L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private JugadorService jugadorService;

    // ============================================================== GET /jugadores

    @Test
    @DisplayName("GET /jugadores sin token: 401 con codigo NO_AUTENTICADO y sin llamar al servicio")
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(jugadorService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /jugadores con USER o ADMIN y sin filtro: 200 con la página y equipoId nulo para el servicio")
    void listar_conCualquierRol_devuelve200(String rol) throws Exception {
        when(jugadorService.listar(isNull(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(jugadorResponse()), 0, 20, 1, 1));

        mockMvc.perform(get(RUTA).header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(100))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.contenido[0].equipo.id").value(10))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("GET /jugadores?equipoId=10 pasa el filtro y el Pageable del cliente al servicio")
    void listar_conFiltroDeEquipo_loPasaAlServicio() throws Exception {
        when(jugadorService.listar(any(Long.class), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(RUTA).param("equipoId", "10").param("page", "1").param("size", "5")
                        .param("sort", "dorsal,desc")
                        .header("Authorization", autorizacion("USER")))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> equipo = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jugadorService).listar(equipo.capture(), pageable.capture());
        assertThat(equipo.getValue()).isEqualTo(10L);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "dorsal"));
    }

    @Test
    @DisplayName("GET /jugadores?equipoId=abc (no numérico): 400 PARAMETRO_INVALIDO")
    void listar_equipoIdNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA).param("equipoId", "abc").header("Authorization", autorizacion("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("GET /jugadores?equipoId=99 de un equipo que no existe: 404")
    void listar_equipoInexistente_devuelve404() throws Exception {
        when(jugadorService.listar(any(Long.class), any(Pageable.class)))
                .thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(get(RUTA).param("equipoId", "99").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ========================================================== GET /jugadores/{id}

    @Test
    @DisplayName("GET /jugadores/{id} sin token: 401")
    void obtener_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/100")).andExpect(status().isUnauthorized());

        verifyNoInteractions(jugadorService);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"USER", "ADMIN"})
    @DisplayName("GET /jugadores/{id} con USER o ADMIN: 200 con el jugador")
    void obtener_conCualquierRol_devuelve200(String rol) throws Exception {
        when(jugadorService.obtener(100L)).thenReturn(jugadorResponse());

        mockMvc.perform(get(RUTA + "/100").header("Authorization", autorizacion(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.fechaNacimiento").value("2000-05-17"))
                .andExpect(jsonPath("$.posicion").value("DELANTERO"))
                .andExpect(jsonPath("$.dorsal").value(9));
    }

    @Test
    @DisplayName("GET /jugadores/{id} de un jugador que no existe: 404 con codigo RECURSO_NO_ENCONTRADO")
    void obtener_inexistente_devuelve404() throws Exception {
        when(jugadorService.obtener(999L)).thenThrow(new RecursoNoEncontradoException("Jugador", 999L));

        mockMvc.perform(get(RUTA + "/999").header("Authorization", autorizacion("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ============================================================== POST /jugadores

    @Test
    @DisplayName("POST /jugadores sin token: 401 y sin llamar al servicio")
    void crear_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("POST /jugadores con USER: 403 ACCESO_DENEGADO y sin llamar al servicio")
    void crear_conUser_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("POST /jugadores con ADMIN: 201, cabecera Location del nuevo jugador y el cuerpo creado")
    void crear_conAdmin_devuelve201ConLocation() throws Exception {
        when(jugadorService.crear(PETICION_VALIDA)).thenReturn(jugadorResponse());

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/jugadores/100"))
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.nombre").value("Juan Perez"));

        verify(jugadorService).crear(PETICION_VALIDA);
    }

    @Test
    @DisplayName("POST /jugadores con campos vacíos: 400 VALIDACION con el detalle por campo")
    void crear_cuerpoVacio_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("nombre:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("fechaNacimiento:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("posicion:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("dorsal:"))))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("equipoId:"))));

        verifyNoInteractions(jugadorService);
    }

    @ParameterizedTest(name = "dorsal {0}")
    @ValueSource(ints = {0, -1, 100, 1000})
    @DisplayName("POST /jugadores con dorsal fuera de 1 a 99: 400 y sin llamar al servicio")
    void crear_dorsalFueraDeRango_devuelve400(int dorsal) throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("\"dorsal\": 9", "\"dorsal\": " + dorsal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*]", hasItem(startsWith("dorsal:"))));

        verifyNoInteractions(jugadorService);
    }

    @ParameterizedTest(name = "dorsal {0}")
    @ValueSource(ints = {1, 99})
    @DisplayName("POST /jugadores con dorsal en los extremos válidos (1 y 99): 201")
    void crear_dorsalEnLosExtremos_devuelve201(int dorsal) throws Exception {
        when(jugadorService.crear(any(JugadorRequest.class))).thenReturn(jugadorResponse());

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("\"dorsal\": 9", "\"dorsal\": " + dorsal)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /jugadores con una posición inexistente o una fecha mal escrita: 400 CUERPO_INVALIDO")
    void crear_posicionOFechaInvalidas_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("DELANTERO", "ARQUERO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("2000-05-17", "17/05/2000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("POST /jugadores con un dorsal ocupado: 409 con codigo DORSAL_DUPLICADO")
    void crear_dorsalDuplicado_devuelve409() throws Exception {
        when(jugadorService.crear(PETICION_VALIDA))
                .thenThrow(new ConflictoException("DORSAL_DUPLICADO", "El dorsal 9 ya esta ocupado."));

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DORSAL_DUPLICADO"));
    }

    @Test
    @DisplayName("POST /jugadores con una fecha de nacimiento futura: 422 con codigo FECHA_NACIMIENTO_FUTURA")
    void crear_fechaNacimientoFutura_devuelve422() throws Exception {
        when(jugadorService.crear(PETICION_VALIDA))
                .thenThrow(new ReglaNegocioException("FECHA_NACIMIENTO_FUTURA", "La fecha es futura."));

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("FECHA_NACIMIENTO_FUTURA"));
    }

    @Test
    @DisplayName("POST /jugadores con un equipo que no existe: 404")
    void crear_equipoInexistente_devuelve404() throws Exception {
        when(jugadorService.crear(PETICION_VALIDA)).thenThrow(new RecursoNoEncontradoException("Equipo", 10L));

        mockMvc.perform(post(RUTA).header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    // ========================================================= PUT /jugadores/{id}

    @Test
    @DisplayName("PUT /jugadores/{id} sin token: 401")
    void actualizar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(put(RUTA + "/100").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("PUT /jugadores/{id} con USER: 403")
    void actualizar_conUser_devuelve403() throws Exception {
        mockMvc.perform(put(RUTA + "/100").header("Authorization", autorizacion("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("PUT /jugadores/{id} con ADMIN: 200 con el jugador actualizado (RN-02: el servicio recibe el nuevo equipoId)")
    void actualizar_conAdmin_devuelve200() throws Exception {
        when(jugadorService.actualizar(100L, PETICION_VALIDA)).thenReturn(jugadorResponse());

        mockMvc.perform(put(RUTA + "/100").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));

        verify(jugadorService).actualizar(100L, PETICION_VALIDA);
    }

    @Test
    @DisplayName("PUT /jugadores/{id} con cuerpo inválido: 400 y sin llamar al servicio")
    void actualizar_cuerpoInvalido_devuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/100").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("PUT /jugadores/{id} con el dorsal ocupado en el equipo destino (RN-02): 409 DORSAL_DUPLICADO")
    void actualizar_rn02_dorsalOcupadoEnElDestino_devuelve409() throws Exception {
        when(jugadorService.actualizar(100L, PETICION_VALIDA))
                .thenThrow(new ConflictoException("DORSAL_DUPLICADO", "El dorsal 9 ya esta ocupado."));

        mockMvc.perform(put(RUTA + "/100").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DORSAL_DUPLICADO"));
    }

    @Test
    @DisplayName("PUT /jugadores/{id} de un jugador que no existe: 404")
    void actualizar_inexistente_devuelve404() throws Exception {
        when(jugadorService.actualizar(999L, PETICION_VALIDA)).thenThrow(new RecursoNoEncontradoException("Jugador", 999L));

        mockMvc.perform(put(RUTA + "/999").header("Authorization", autorizacion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    // ====================================================== DELETE /jugadores/{id}

    @Test
    @DisplayName("DELETE /jugadores/{id} sin token: 401")
    void eliminar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete(RUTA + "/100")).andExpect(status().isUnauthorized());

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("DELETE /jugadores/{id} con USER: 403")
    void eliminar_conUser_devuelve403() throws Exception {
        mockMvc.perform(delete(RUTA + "/100").header("Authorization", autorizacion("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jugadorService);
    }

    @Test
    @DisplayName("DELETE /jugadores/{id} con ADMIN: 204 sin cuerpo")
    void eliminar_conAdmin_devuelve204() throws Exception {
        mockMvc.perform(delete(RUTA + "/100").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNoContent());

        verify(jugadorService).eliminar(100L);
    }

    @Test
    @DisplayName("DELETE /jugadores/{id} de un jugador con goles o tarjetas: 409 CONFLICTO_DEPENDENCIAS")
    void eliminar_conDependientes_devuelve409() throws Exception {
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "tiene goles"))
                .when(jugadorService).eliminar(100L);

        mockMvc.perform(delete(RUTA + "/100").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));
    }

    @Test
    @DisplayName("DELETE /jugadores/{id} de un jugador que no existe: 404")
    void eliminar_inexistente_devuelve404() throws Exception {
        doThrow(new RecursoNoEncontradoException("Jugador", 999L)).when(jugadorService).eliminar(999L);

        mockMvc.perform(delete(RUTA + "/999").header("Authorization", autorizacion("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ================================================================== Apoyo

    private String autorizacion(String rol) {
        return "ADMIN".equals(rol)
                ? "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN)
                : "Bearer " + jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }

    private static JugadorResponse jugadorResponse() {
        return new JugadorResponse(100L, "Juan Perez", LocalDate.of(2000, 5, 17), PosicionJugador.DELANTERO, 9,
                new ResumenResponse(10L, "Deportivo Cali"));
    }
}

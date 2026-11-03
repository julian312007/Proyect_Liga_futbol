package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.dto.response.SancionJugadorResponse;
import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.GoleadoresService;
import co.edu.uniajc.liga.service.SancionService;
import co.edu.uniajc.liga.service.TablaPosicionesService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de acceso y contrato HTTP de {@link EstadisticasLigaController} con {@code @WebMvcTest}: sin base de
 * datos ni Docker. La seguridad es la real (cadena de filtros, JWT propio y handlers 401/403); los servicios se
 * simulan. Todos los endpoints son de lectura, abiertos a {@code USER} y {@code ADMIN}.
 */
@WebMvcTest(EstadisticasLigaController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class EstadisticasLigaControllerTest {

    private static final String BASE = "/api/v1/ligas/7";
    private static final String TABLA = BASE + "/tabla-posiciones";
    private static final String GOLEADORES = BASE + "/goleadores";
    private static final String SANCIONES = BASE + "/sanciones";
    private static final String SUSPENDIDOS = BASE + "/sanciones/suspendidos?jornada=2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TablaPosicionesService tablaPosicionesService;

    @MockitoBean
    private GoleadoresService goleadoresService;

    @MockitoBean
    private SancionService sancionService;

    // ================================================================= 401

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {TABLA, GOLEADORES, SANCIONES, SUSPENDIDOS})
    @DisplayName("Sin token: 401 con el JSON de error y sin llamar a ningún servicio")
    void sinToken_devuelve401(String ruta) throws Exception {
        mockMvc.perform(get(ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(tablaPosicionesService, goleadoresService, sancionService);
    }

    // ================================================================= 200

    @ParameterizedTest(name = "USER en GET {0}")
    @ValueSource(strings = {TABLA, GOLEADORES, SANCIONES, SUSPENDIDOS})
    @DisplayName("USER puede consultar las estadísticas: 200")
    void user_devuelve200(String ruta) throws Exception {
        mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "ADMIN en GET {0}")
    @ValueSource(strings = {TABLA, GOLEADORES, SANCIONES, SUSPENDIDOS})
    @DisplayName("ADMIN puede consultar las estadísticas: 200")
    void admin_devuelve200(String ruta) throws Exception {
        mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La tabla de posiciones devuelve PJ, PG, PE, PP, GF, GC, DG y Pts de cada equipo")
    void tablaDePosiciones_devuelveLasColumnasDelDocumento() throws Exception {
        when(tablaPosicionesService.obtenerTabla(7L)).thenReturn(List.of(
                new PosicionEquipoResponse(1, 1L, "Aguilas", 2, 1, 1, 0, 3, 1, 2, 4)));

        mockMvc.perform(get(TABLA).header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].posicion").value(1))
                .andExpect(jsonPath("$[0].equipoId").value(1))
                .andExpect(jsonPath("$[0].equipo").value("Aguilas"))
                .andExpect(jsonPath("$[0].pj").value(2))
                .andExpect(jsonPath("$[0].pg").value(1))
                .andExpect(jsonPath("$[0].pe").value(1))
                .andExpect(jsonPath("$[0].pp").value(0))
                .andExpect(jsonPath("$[0].gf").value(3))
                .andExpect(jsonPath("$[0].gc").value(1))
                .andExpect(jsonPath("$[0].dg").value(2))
                .andExpect(jsonPath("$[0].pts").value(4));
    }

    @Test
    @DisplayName("Los goleadores devuelven jugador, equipo y goles")
    void goleadores_devuelveJugadorEquipoYGoles() throws Exception {
        when(goleadoresService.obtenerGoleadores(7L)).thenReturn(List.of(
                new GoleadorResponse(1, 10L, "Ana Ruiz", 1L, "Aguilas", 3)));

        mockMvc.perform(get(GOLEADORES).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jugador").value("Ana Ruiz"))
                .andExpect(jsonPath("$[0].equipo").value("Aguilas"))
                .andExpect(jsonPath("$[0].goles").value(3));
    }

    @Test
    @DisplayName("Las sanciones devuelven el conteo de amarillas y rojas por jugador")
    void sanciones_devuelveAmarillasYRojas() throws Exception {
        when(sancionService.listarSanciones(7L)).thenReturn(List.of(
                new SancionJugadorResponse(10L, "Ana Ruiz", 1L, "Aguilas", 2, 1)));

        mockMvc.perform(get(SANCIONES).header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jugador").value("Ana Ruiz"))
                .andExpect(jsonPath("$[0].amarillas").value(2))
                .andExpect(jsonPath("$[0].rojas").value(1));
    }

    @Test
    @DisplayName("Los suspendidos de la jornada se piden al servicio con la liga y la jornada de la URL")
    void suspendidos_delegaConLigaYJornada() throws Exception {
        when(sancionService.listarSuspendidos(7L, 2)).thenReturn(List.of(
                new SuspendidoResponse(11L, "Luis Mora", 2L, "Buhos", 5L, 1)));

        mockMvc.perform(get(SUSPENDIDOS).header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jugador").value("Luis Mora"))
                .andExpect(jsonPath("$[0].partidoRojaId").value(5))
                .andExpect(jsonPath("$[0].jornadaRoja").value(1));

        verify(sancionService).listarSuspendidos(7L, 2);
    }

    // ================================================================= 404

    @Test
    @DisplayName("Tabla de una liga inexistente: 404 con codigo RECURSO_NO_ENCONTRADO")
    void tabla_ligaInexistente_devuelve404() throws Exception {
        when(tablaPosicionesService.obtenerTabla(99L)).thenThrow(new RecursoNoEncontradoException("Liga", 99L));

        mockMvc.perform(get("/api/v1/ligas/99/tabla-posiciones").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Goleadores, sanciones y suspendidos de una liga inexistente: 404")
    void demasConsultas_ligaInexistente_devuelve404() throws Exception {
        when(goleadoresService.obtenerGoleadores(99L)).thenThrow(new RecursoNoEncontradoException("Liga", 99L));
        when(sancionService.listarSanciones(99L)).thenThrow(new RecursoNoEncontradoException("Liga", 99L));
        when(sancionService.listarSuspendidos(99L, 1)).thenThrow(new RecursoNoEncontradoException("Liga", 99L));
        String autorizacion = "Bearer " + tokenAdmin();

        mockMvc.perform(get("/api/v1/ligas/99/goleadores").header("Authorization", autorizacion))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ligas/99/sanciones").header("Authorization", autorizacion))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ligas/99/sanciones/suspendidos?jornada=1").header("Authorization", autorizacion))
                .andExpect(status().isNotFound());
    }

    // ================================================================= 400

    @Test
    @DisplayName("Suspendidos sin el parámetro jornada: 400 y sin llamar al servicio")
    void suspendidos_sinJornada_devuelve400() throws Exception {
        mockMvc.perform(get(BASE + "/sanciones/suspendidos").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").exists());

        verifyNoInteractions(sancionService);
    }

    @ParameterizedTest(name = "jornada={0}")
    @ValueSource(strings = {"0", "-3"})
    @DisplayName("Suspendidos con una jornada menor que 1: 400 y sin llamar al servicio")
    void suspendidos_jornadaMenorQueUno_devuelve400(String jornada) throws Exception {
        mockMvc.perform(get(BASE + "/sanciones/suspendidos?jornada=" + jornada)
                        .header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").exists());

        verifyNoInteractions(sancionService);
    }

    @Test
    @DisplayName("Suspendidos con una jornada que no es un número: 400 PARAMETRO_INVALIDO")
    void suspendidos_jornadaNoNumerica_devuelve400() throws Exception {
        mockMvc.perform(get(BASE + "/sanciones/suspendidos?jornada=abc")
                        .header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(sancionService);
    }

    @Test
    @DisplayName("Un id de liga que no es un número: 400 PARAMETRO_INVALIDO")
    void idDeLigaNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/ligas/abc/tabla-posiciones").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(tablaPosicionesService);
    }

    // =========================================================== Solo lectura

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("Las estadísticas son de solo lectura: escribir, incluso como ADMIN, da 405")
    void escritura_noExiste_devuelve405(String metodo) throws Exception {
        var solicitud = switch (metodo) {
            case "POST" -> post(TABLA);
            case "PUT" -> put(TABLA);
            default -> delete(TABLA);
        };

        mockMvc.perform(solicitud.header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(tablaPosicionesService);
    }

    // ================================================================ Apoyo

    private String tokenAdmin() {
        return jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String tokenUser() {
        return jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }
}

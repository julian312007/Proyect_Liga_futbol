package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse.Resultado;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.HistorialEquipoService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * Matriz de acceso y contrato HTTP de {@link HistorialEquipoController} con {@code @WebMvcTest}: sin base de
 * datos ni Docker. Seguridad real, servicio simulado. El único endpoint es de lectura, abierto a {@code USER} y
 * {@code ADMIN}.
 */
@WebMvcTest(HistorialEquipoController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class HistorialEquipoControllerTest {

    private static final String RUTA = "/api/v1/equipos/1/partidos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private HistorialEquipoService historialEquipoService;

    @Test
    @DisplayName("Sin token: 401 con el JSON de error y sin llamar al servicio")
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(historialEquipoService);
    }

    @Test
    @DisplayName("USER puede consultar el historial: 200 con la página y los partidos vistos desde el equipo")
    void user_devuelve200ConLaPagina() throws Exception {
        PartidoHistorialResponse partido = new PartidoHistorialResponse(50L, new ResumenResponse(7L, "Liga Aguila"), 1,
                Instant.parse("2026-03-01T20:00:00Z"), new ResumenResponse(1L, "Aguilas"),
                new ResumenResponse(2L, "Buhos"), 2, 0, EstadoPartido.FINALIZADO, true, Resultado.VICTORIA);
        when(historialEquipoService.obtenerHistorial(eq(1L), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(partido), 0, 20, 1, 1));

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.contenido[0].id").value(50))
                .andExpect(jsonPath("$.contenido[0].liga.nombre").value("Liga Aguila"))
                .andExpect(jsonPath("$.contenido[0].equipoLocal.id").value(1))
                .andExpect(jsonPath("$.contenido[0].estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.contenido[0].esLocal").value(true))
                .andExpect(jsonPath("$.contenido[0].resultado").value("VICTORIA"))
                .andExpect(jsonPath("$.contenido[0].fechaHora").value("2026-03-01T20:00:00Z"));
    }

    @Test
    @DisplayName("ADMIN puede consultar el historial: 200")
    void admin_devuelve200() throws Exception {
        when(historialEquipoService.obtenerHistorial(eq(1L), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }

    @Test
    @DisplayName("La paginación de la URL (?page=&size=&sort=) llega al servicio")
    void paginacionDeLaUrl_llegaAlServicio() throws Exception {
        when(historialEquipoService.obtenerHistorial(eq(1L), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(RUTA + "?page=1&size=5&sort=jornada,asc").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(historialEquipoService).obtenerHistorial(eq(1L), captura.capture());
        assertThat(captura.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captura.getValue().getPageSize()).isEqualTo(5);
        assertThat(captura.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "jornada"));
    }

    @Test
    @DisplayName("Equipo inexistente: 404 con codigo RECURSO_NO_ENCONTRADO")
    void equipoInexistente_devuelve404() throws Exception {
        when(historialEquipoService.obtenerHistorial(eq(99L), any(Pageable.class)))
                .thenThrow(new RecursoNoEncontradoException("Equipo", 99L));

        mockMvc.perform(get("/api/v1/equipos/99/partidos").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Un id de equipo que no es un número: 400 PARAMETRO_INVALIDO")
    void idNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/equipos/abc/partidos").header("Authorization", "Bearer " + tokenUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(historialEquipoService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("El historial es de solo lectura: escribir, incluso como ADMIN, da 405")
    void escritura_noExiste_devuelve405(String metodo) throws Exception {
        var solicitud = switch (metodo) {
            case "POST" -> post(RUTA);
            case "PUT" -> put(RUTA);
            default -> delete(RUTA);
        };

        mockMvc.perform(solicitud.header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(historialEquipoService);
    }

    private String tokenAdmin() {
        return jwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN);
    }

    private String tokenUser() {
        return jwtService.generarToken(UsuariosDePruebaConfig.ANA, Rol.USER);
    }
}

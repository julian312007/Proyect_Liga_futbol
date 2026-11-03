package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.SecurityConfig;
import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import co.edu.uniajc.liga.service.EstadioService;
import co.edu.uniajc.liga.support.UsuariosDePruebaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
 * {@code EstadioController} con {@code @WebMvcTest}: el controlador de referencia de D-4. Sin base de datos ni
 * Docker; la seguridad es la real y el servicio se simula. Hereda de {@link MatrizRolesCatalogo} la matriz
 * completa de roles (401 / 403 / 2xx por endpoint) y el contrato HTTP común, y añade las reglas EST-01, EST-02
 * y EST-03 vistas desde HTTP (códigos y estados).
 */
@WebMvcTest(EstadioController.class)
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, UsuariosDePruebaConfig.class})
@ActiveProfiles("test")
class EstadioControllerTest extends MatrizRolesCatalogo {

    private static final String RUTA = "/api/v1/estadios";
    private static final String CUERPO_VALIDO = """
            {"nombre": "Estadio Pascual Guerrero", "ciudad": "Cali", "capacidad": 38000}""";
    private static final EstadioResponse ESTADIO = new EstadioResponse(1L, "Estadio Pascual Guerrero", "Cali", 38_000);

    @MockitoBean
    private EstadioService estadioService;

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
        when(estadioService.listar(any())).thenReturn(new PaginaResponse<>(List.of(ESTADIO), 0, 20, 1, 1));
        when(estadioService.obtener(1L)).thenReturn(ESTADIO);
        when(estadioService.crear(any(EstadioRequest.class))).thenReturn(ESTADIO);
        when(estadioService.actualizar(eq(1L), any(EstadioRequest.class))).thenReturn(ESTADIO);
    }

    @Override
    protected void verificarServicioSinUso() {
        verifyNoInteractions(estadioService);
    }

    // ================================================================ EST-03 (400)

    @Test
    @DisplayName("EST-03 positivo: capacidad 1 (el mínimo) con ciudad es válida: 201")
    void est03_capacidadMinima_devuelve201() throws Exception {
        when(estadioService.crear(any(EstadioRequest.class))).thenReturn(ESTADIO);

        enviar(HttpMethod.POST, RUTA, """
                {"nombre": "Estadio", "ciudad": "Cali", "capacidad": 1}""", admin())
                .andExpect(status().isCreated());

        verify(estadioService).crear(new EstadioRequest("Estadio", "Cali", 1));
    }

    static Stream<Arguments> cuerposQueIncumplenEst03() {
        return Stream.of(
                Arguments.of("capacidad cero", """
                        {"nombre": "Estadio", "ciudad": "Cali", "capacidad": 0}""", "capacidad"),
                Arguments.of("capacidad negativa", """
                        {"nombre": "Estadio", "ciudad": "Cali", "capacidad": -500}""", "capacidad"),
                Arguments.of("capacidad ausente", """
                        {"nombre": "Estadio", "ciudad": "Cali"}""", "capacidad"),
                Arguments.of("ciudad vacía", """
                        {"nombre": "Estadio", "ciudad": "", "capacidad": 100}""", "ciudad"),
                Arguments.of("ciudad solo espacios", """
                        {"nombre": "Estadio", "ciudad": "   ", "capacidad": 100}""", "ciudad"),
                Arguments.of("ciudad ausente", """
                        {"nombre": "Estadio", "capacidad": 100}""", "ciudad"),
                Arguments.of("nombre vacío", """
                        {"nombre": "", "ciudad": "Cali", "capacidad": 100}""", "nombre"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cuerposQueIncumplenEst03")
    @DisplayName("EST-03 negativo: capacidad no positiva o ciudad/nombre ausentes dan 400 VALIDACION con el campo en los detalles (POST y PUT)")
    void est03_cuerpoInvalido_devuelve400(String descripcion, String cuerpo, String campo) throws Exception {
        for (HttpMethod metodo : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String ruta = metodo == HttpMethod.POST ? RUTA : RUTA + "/1";
            enviar(metodo, ruta, cuerpo, admin())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles", hasItem(startsWith(campo + ": "))));
        }

        verifyNoInteractions(estadioService);
    }

    @Test
    @DisplayName("EST-03 negativo: una capacidad que no es número (texto) da 400 CUERPO_INVALIDO")
    void est03_capacidadNoNumerica_devuelve400() throws Exception {
        enviar(HttpMethod.POST, RUTA, """
                {"nombre": "Estadio", "ciudad": "Cali", "capacidad": "muchos"}""", admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));

        verifyNoInteractions(estadioService);
    }

    @Test
    @DisplayName("El cliente no fija el id: un campo 'id' en el cuerpo se ignora y el servicio recibe solo los datos del estadio")
    void cuerpoConId_seIgnora() throws Exception {
        enviar(HttpMethod.POST, RUTA, """
                {"id": 999, "nombre": "Estadio", "ciudad": "Cali", "capacidad": 10}""", admin())
                .andExpect(status().isCreated());

        verify(estadioService).crear(new EstadioRequest("Estadio", "Cali", 10));
    }

    // ====================================================== EST-01 y EST-02 (409), 404

    @Test
    @DisplayName("EST-01: si el servicio detecta el nombre duplicado en la ciudad, POST responde 409 con código EST-01")
    void est01_crear_duplicado_devuelve409() throws Exception {
        when(estadioService.crear(any(EstadioRequest.class)))
                .thenThrow(new ConflictoException("EST-01", "Ya existe un estadio llamado 'X' en Cali."));

        enviar(HttpMethod.POST, RUTA, CUERPO_VALIDO, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("EST-01"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe un estadio llamado 'X' en Cali."))
                .andExpect(jsonPath("$.ruta").value(RUTA));
    }

    @Test
    @DisplayName("EST-01: PUT al nombre de otro estadio responde 409 con código EST-01")
    void est01_actualizar_duplicado_devuelve409() throws Exception {
        when(estadioService.actualizar(eq(1L), any(EstadioRequest.class)))
                .thenThrow(new ConflictoException("EST-01", "Ya existe otro estadio con ese nombre."));

        enviar(HttpMethod.PUT, RUTA + "/1", CUERPO_VALIDO, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EST-01"));
    }

    @Test
    @DisplayName("EST-02 negativo: DELETE de un estadio con equipos o partidos responde 409 con código EST-02")
    void est02_eliminar_conDependientes_devuelve409() throws Exception {
        doThrow(new ConflictoException("EST-02", "El estadio es local de al menos un equipo"))
                .when(estadioService).eliminar(1L);

        enviar(HttpMethod.DELETE, RUTA + "/1", null, admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EST-02"));
    }

    @Test
    @DisplayName("EST-02 positivo: DELETE de un estadio sin dependientes responde 204 y llega al servicio")
    void est02_eliminar_sinDependientes_devuelve204() throws Exception {
        enviar(HttpMethod.DELETE, RUTA + "/1", null, admin())
                .andExpect(status().isNoContent());

        verify(estadioService).eliminar(1L);
    }

    @Test
    @DisplayName("Un estadio inexistente: GET, PUT y DELETE responden 404 RECURSO_NO_ENCONTRADO")
    void estadioInexistente_devuelve404() throws Exception {
        when(estadioService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Estadio", 99L));
        when(estadioService.actualizar(eq(99L), any(EstadioRequest.class)))
                .thenThrow(new RecursoNoEncontradoException("Estadio", 99L));
        doThrow(new RecursoNoEncontradoException("Estadio", 99L))
                .when(estadioService).eliminar(99L);

        enviar(HttpMethod.GET, RUTA + "/99", null, user())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Estadio con id 99 no existe"));
        enviar(HttpMethod.PUT, RUTA + "/99", CUERPO_VALIDO, admin()).andExpect(status().isNotFound());
        enviar(HttpMethod.DELETE, RUTA + "/99", null, admin()).andExpect(status().isNotFound());
    }

    // ============================================================== paginación

    @Test
    @DisplayName("GET /estadios entrega al servicio la página, el tamaño y el orden pedidos en la URL")
    void listar_pasaLaPaginacionAlServicio() throws Exception {
        enviar(HttpMethod.GET, RUTA + "?page=2&size=5&sort=ciudad,desc", null, user())
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(estadioService).listar(pedido.capture());
        assertThat(pedido.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pedido.getValue().getPageSize()).isEqualTo(5);
        assertThat(pedido.getValue().getSort().getOrderFor("ciudad")).isNotNull();
        assertThat(pedido.getValue().getSort().getOrderFor("ciudad").isDescending()).isTrue();
    }

    @Test
    @DisplayName("GET /estadios devuelve el estadio con todos sus campos")
    void listar_devuelveLosCamposDelEstadio() throws Exception {
        enviar(HttpMethod.GET, RUTA, null, user())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].nombre").value("Estadio Pascual Guerrero"))
                .andExpect(jsonPath("$.contenido[0].ciudad").value("Cali"))
                .andExpect(jsonPath("$.contenido[0].capacidad").value(38_000));
    }
}

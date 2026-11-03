package co.edu.uniajc.liga.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba unitaria del formato de error de la API. No levanta Spring ni necesita Docker:
 * usa un controlador de prueba que lanza cada excepción y comprueba el {@link ApiError} resultante.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Recurso inexistente: 404 con código RECURSO_NO_ENCONTRADO y la ruta consultada")
    void recursoNoEncontrado_devuelve404() throws Exception {
        mockMvc.perform(get("/prueba/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Equipo con id 99 no existe"))
                .andExpect(jsonPath("$.ruta").value("/prueba/no-encontrado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Conflicto de dominio: 409 y el código de la regla (RN-05)")
    void conflicto_devuelve409ConCodigoDeLaRegla() throws Exception {
        mockMvc.perform(get("/prueba/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-05"))
                .andExpect(jsonPath("$.mensaje").value("El equipo ya juega en esta jornada"));
    }

    @Test
    @DisplayName("Regla de negocio incumplida: 422 y el código de la regla (RN-04)")
    void reglaNegocio_devuelve422ConCodigoDeLaRegla() throws Exception {
        mockMvc.perform(get("/prueba/regla"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.codigo").value("RN-04"));
    }

    @Test
    @DisplayName("Validación del cuerpo: 400 con el detalle por campo")
    void validacion_devuelve400ConDetallePorCampo() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0]").value(startsWith("nombre:")));
    }

    @Test
    @DisplayName("JSON mal formado: 400 CUERPO_INVALIDO sin revelar detalles del parser")
    void jsonMalFormado_devuelve400() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"))
                .andExpect(content().string(not(containsString("Jackson"))));
    }

    @Test
    @DisplayName("Parámetro de ruta con tipo incorrecto: 400 PARAMETRO_INVALIDO")
    void parametroConFormatoIncorrecto_devuelve400() throws Exception {
        mockMvc.perform(get("/prueba/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    @DisplayName("Método HTTP no permitido: conserva el 405 y el encabezado Allow")
    void metodoNoPermitido_conservaElEstadoHttp() throws Exception {
        mockMvc.perform(get("/prueba/validar"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("HTTP_405"))
                .andExpect(header().exists("Allow"));
    }

    @Test
    @DisplayName("Error inesperado: 500 genérico que no filtra el mensaje interno")
    void errorInesperado_devuelve500SinFiltrarDetalles() throws Exception {
        mockMvc.perform(get("/prueba/explota"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(not(containsString("clave interna secreta"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    /** Controlador solo para esta prueba: lanza cada tipo de excepción. */
    @RestController
    @RequestMapping("/prueba")
    public static class ControladorDePrueba {

        @GetMapping("/no-encontrado")
        public void noEncontrado() {
            throw new RecursoNoEncontradoException("Equipo", 99);
        }

        @GetMapping("/conflicto")
        public void conflicto() {
            throw new ConflictoException("RN-05", "El equipo ya juega en esta jornada");
        }

        @GetMapping("/regla")
        public void regla() {
            throw new ReglaNegocioException("RN-04", "Un equipo no puede enfrentarse a sí mismo");
        }

        @GetMapping("/explota")
        public void explota() {
            throw new IllegalStateException("clave interna secreta");
        }

        @GetMapping("/numero/{id}")
        public String numero(@PathVariable("id") Integer id) {
            return "ok " + id;
        }

        @PostMapping("/validar")
        public void validar(@Valid @RequestBody SolicitudDePrueba solicitud) {
            // Sin lógica: solo interesa la validación.
        }
    }

    public record SolicitudDePrueba(@NotBlank String nombre) {
    }
}

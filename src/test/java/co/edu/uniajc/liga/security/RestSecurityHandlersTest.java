package co.edu.uniajc.liga.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba unitaria de los handlers 401 y 403: sin Spring ni Docker. Comprueba el estado HTTP y que el
 * cuerpo sea un {@code ApiError} (el recorrido completo por la cadena de filtros está en
 * {@code SecurityConfigTest}).
 */
class RestSecurityHandlersTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    @DisplayName("401: estado, tipo de contenido, WWW-Authenticate y cuerpo ApiError con la ruta pedida")
    void entryPoint_respondeApiError401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(jsonMapper)
                .commence(request, response, new BadCredentialsException("detalle interno que no debe salir"));

        String cuerpo = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(JsonPath.<Integer>read(cuerpo, "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo, "$.codigo")).isEqualTo("NO_AUTENTICADO");
        assertThat(JsonPath.<String>read(cuerpo, "$.ruta")).isEqualTo("/api/v1/auth/me");
        assertThat(JsonPath.<Object>read(cuerpo, "$.timestamp")).isNotNull();
        assertThat(JsonPath.<java.util.List<Object>>read(cuerpo, "$.detalles")).isEmpty();
        // El mensaje de la excepción original jamás llega al cliente.
        assertThat(cuerpo).doesNotContain("detalle interno");
    }

    @Test
    @DisplayName("403: estado, tipo de contenido y cuerpo ApiError con la ruta pedida")
    void accessDeniedHandler_respondeApiError403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/estadios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(jsonMapper)
                .handle(request, response, new AccessDeniedException("detalle interno que no debe salir"));

        String cuerpo = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(JsonPath.<Integer>read(cuerpo, "$.status")).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo, "$.codigo")).isEqualTo("ACCESO_DENEGADO");
        assertThat(JsonPath.<String>read(cuerpo, "$.ruta")).isEqualTo("/api/v1/estadios");
        assertThat(cuerpo).doesNotContain("detalle interno");
    }
}

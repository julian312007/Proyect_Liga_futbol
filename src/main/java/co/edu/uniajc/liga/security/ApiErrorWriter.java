package co.edu.uniajc.liga.security;

import co.edu.uniajc.liga.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Escribe un {@link ApiError} como respuesta JSON desde la cadena de filtros de Spring Security.
 *
 * <p>Los errores de seguridad que ocurren <em>antes</em> de llegar a un controlador (sin token, token
 * inválido, acceso denegado por regla de ruta) no pasan por {@code GlobalExceptionHandler}, porque
 * ese solo ve las excepciones lanzadas dentro de Spring MVC. Este apoyo les da el mismo formato.
 *
 * <p>Los códigos y mensajes de 401 y 403 están repetidos en {@code GlobalExceptionHandler}
 * ({@code NO_AUTENTICADO} y {@code ACCESO_DENEGADO}); si se cambian allí, hay que cambiarlos aquí.
 * Una prueba ({@code SecurityConfigTest}) verifica que ambos caminos devuelvan lo mismo.
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void escribir(JsonMapper jsonMapper, HttpServletRequest request, HttpServletResponse response,
                         int status, String codigo, String mensaje) throws IOException {
        ApiError cuerpo = new ApiError(Instant.now(), status, codigo, mensaje, request.getRequestURI(), List.of());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(cuerpo));
    }
}

package co.edu.uniajc.liga.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Responde {@code 403} con el formato {@code ApiError} cuando un usuario autenticado intenta algo
 * que su rol no permite y la regla se evalúa dentro de la cadena de filtros.
 *
 * <p>Las denegaciones de {@code @PreAuthorize} ocurren dentro de un controlador y las atiende
 * {@code GlobalExceptionHandler}; ambos caminos devuelven el mismo cuerpo.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    static final String CODIGO = "ACCESO_DENEGADO";
    static final String MENSAJE = "No tiene permisos para realizar esta operación.";

    private final JsonMapper jsonMapper;

    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorWriter.escribir(jsonMapper, request, response, HttpStatus.FORBIDDEN.value(), CODIGO, MENSAJE);
    }
}

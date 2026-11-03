package co.edu.uniajc.liga.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Responde {@code 401} con el formato {@code ApiError} cuando se pide una ruta protegida sin
 * autenticación válida (sin token, token vencido, manipulado o de un usuario inexistente).
 *
 * <p>El mensaje es genérico a propósito: no distingue entre "sin token" y "token inválido".
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String CODIGO = "NO_AUTENTICADO";
    static final String MENSAJE = "Autenticación requerida o credenciales inválidas.";

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // RFC 6750: un 401 debe indicar el esquema de autenticación esperado.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ApiErrorWriter.escribir(jsonMapper, request, response, HttpStatus.UNAUTHORIZED.value(), CODIGO, MENSAJE);
    }
}

package co.edu.uniajc.liga.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Único punto donde las excepciones se convierten en respuestas HTTP, siempre con el formato
 * {@link ApiError}.
 *
 * <p>Mapa de errores de la API:
 * <ul>
 *   <li>{@code 400}: validación del cuerpo, parámetros mal formados o cuerpo ilegible.</li>
 *   <li>{@code 401}: sin autenticar o credenciales inválidas.</li>
 *   <li>{@code 403}: autenticado pero sin el rol requerido.</li>
 *   <li>{@code 404}: recurso inexistente.</li>
 *   <li>{@code 409}: conflicto (unicidad, choques de horario, estado, dependencias).</li>
 *   <li>{@code 422}: regla de negocio incumplida.</li>
 *   <li>{@code 500}: error no previsto; el cliente solo recibe un mensaje genérico.</li>
 * </ul>
 *
 * <p>Seguridad: ninguna respuesta incluye trazas, SQL ni nombres de clases. El detalle técnico
 * queda únicamente en el registro (log) del servidor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 422. Se usa el número y no la constante de {@link HttpStatus} porque Spring 7 la está renombrando. */
    private static final HttpStatusCode REGLA_NEGOCIO_INCUMPLIDA = HttpStatusCode.valueOf(422);

    // ---------------------------------------------------------------- Dominio

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> recursoNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return responder(REGLA_NEGOCIO_INCUMPLIDA, ex.getCodigo(), ex.getMessage(), request, List.of());
    }

    // ------------------------------------------------------------- Validación

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacionCuerpo(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> detalles = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(e -> e.getField() + ": " + e.getDefaultMessage()),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(e -> e.getObjectName() + ": " + e.getDefaultMessage()))
                .sorted()
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay errores de validación en la solicitud.",
                request, detalles);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> validacionParametros(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay errores de validación en la solicitud.",
                request, detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // No se devuelve ex.getMessage(): podría revelar clases internas o la estructura del parser.
        return responder(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO",
                "El cuerpo de la solicitud falta, está mal formado o tiene valores con tipo incorrecto.",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro '%s' tiene un valor con formato incorrecto.".formatted(ex.getName()),
                request, List.of());
    }

    // -------------------------------------------------------- Persistencia

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridadDatos(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Última defensa: los índices únicos y las FK de la BD respaldan las validaciones del servicio.
        log.warn("Violación de integridad en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "CONFLICTO_INTEGRIDAD",
                "La operación viola una restricción de integridad: valor duplicado o dato relacionado.",
                request, List.of());
    }

    // ------------------------------------------------------------ Seguridad

    /*
     * Estos dos handlers evitan que el manejador genérico de abajo convierta en 500 las
     * excepciones de seguridad lanzadas desde un controlador (por ejemplo, @PreAuthorize).
     * Las que ocurren dentro de la cadena de filtros las atienden, desde la Fase 1,
     * RestAuthenticationEntryPoint y RestAccessDeniedHandler.
     */

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> noAutenticado(AuthenticationException ex, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Autenticación requerida o credenciales inválidas.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccessDeniedException ex, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No tiene permisos para realizar esta operación.", request, List.of());
    }

    // ------------------------------------------------------------ Respaldo

    /**
     * Respaldo para todo lo demás. Las excepciones estándar de Spring MVC (ruta inexistente,
     * método no permitido, tipo de contenido no soportado, parámetro obligatorio ausente...)
     * implementan {@link ErrorResponse} y conservan su estado HTTP; el resto es un error
     * interno (500) con mensaje genérico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> errorNoControlado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            ApiError cuerpo = crearError(status, "HTTP_" + status.value(), mensajeParaEstado(status),
                    request, List.of());
            return ResponseEntity.status(status).headers(errorResponse.getHeaders()).body(cuerpo);
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error interno. Intente de nuevo más tarde.", request, List.of());
    }

    // ------------------------------------------------------------- Apoyo

    private static String mensajeParaEstado(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "La solicitud no es válida.";
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "Método HTTP no permitido para esta ruta.";
            case 406 -> "Formato de respuesta no soportado.";
            case 415 -> "Tipo de contenido no soportado.";
            default -> status.is5xxServerError()
                    ? "El servicio no pudo procesar la solicitud."
                    : "No se pudo procesar la solicitud.";
        };
    }

    private static ResponseEntity<ApiError> responder(HttpStatusCode status, String codigo, String mensaje,
                                                      HttpServletRequest request, List<String> detalles) {
        return ResponseEntity.status(status).body(crearError(status, codigo, mensaje, request, detalles));
    }

    private static ApiError crearError(HttpStatusCode status, String codigo, String mensaje,
                                       HttpServletRequest request, List<String> detalles) {
        return new ApiError(Instant.now(), status.value(), codigo, mensaje, request.getRequestURI(), detalles);
    }
}

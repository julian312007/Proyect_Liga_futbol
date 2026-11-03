package co.edu.uniajc.liga.exception;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de error de toda la API. Lo produce {@link GlobalExceptionHandler} y, desde la
 * Fase 1, también los handlers de seguridad (401 y 403).
 *
 * @param timestamp instante en que se generó el error
 * @param status    código HTTP (400, 401, 403, 404, 409, 422, 500...)
 * @param codigo    identificador estable para el cliente; en reglas de negocio es el código de la
 *                  regla (por ejemplo {@code RN-05} o {@code EST-02})
 * @param mensaje   explicación legible, en español, sin datos internos (SQL, trazas, rutas de clases)
 * @param ruta      URI de la solicitud que falló
 * @param detalles  detalle por campo en errores de validación; lista vacía en el resto
 */
public record ApiError(
        Instant timestamp,
        int status,
        String codigo,
        String mensaje,
        String ruta,
        List<String> detalles) {
}

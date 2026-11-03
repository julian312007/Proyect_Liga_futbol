package co.edu.uniajc.liga.exception;

/**
 * Raíz de las excepciones de dominio. Lleva un {@code codigo} estable que el cliente recibe en
 * {@link ApiError#codigo()}.
 *
 * <p>Las excepciones de dominio <strong>no conocen HTTP</strong>: es {@link GlobalExceptionHandler}
 * quien traduce cada tipo a su estado ({@code 404}, {@code 409}, {@code 422}). Así los servicios
 * siguen sin depender de la capa web.
 */
public abstract class LigaException extends RuntimeException {

    private final String codigo;

    protected LigaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

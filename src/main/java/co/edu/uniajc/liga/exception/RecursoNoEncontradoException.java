package co.edu.uniajc.liga.exception;

/** El recurso pedido no existe. Se traduce a HTTP 404. */
public class RecursoNoEncontradoException extends LigaException {

    public static final String CODIGO = "RECURSO_NO_ENCONTRADO";

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(CODIGO, "%s con id %s no existe".formatted(recurso, id));
    }
}

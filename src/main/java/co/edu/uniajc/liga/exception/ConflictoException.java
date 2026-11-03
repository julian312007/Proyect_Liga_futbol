package co.edu.uniajc.liga.exception;

/**
 * La operación choca con el estado actual de los datos: unicidad, choques de horario, estado del
 * partido o dependencias que impiden borrar. Se traduce a HTTP 409.
 *
 * <p>Códigos previstos: RN-03, RN-05, RN-06, RN-07, RN-12, EST-01 y EST-02.
 */
public class ConflictoException extends LigaException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}

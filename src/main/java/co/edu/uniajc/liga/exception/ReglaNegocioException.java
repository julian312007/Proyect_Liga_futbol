package co.edu.uniajc.liga.exception;

/**
 * La solicitud está bien formada pero incumple una regla de negocio. Se traduce a HTTP 422.
 *
 * <p>Códigos previstos: RN-04, RN-08, RN-10 y RN-11.
 */
public class ReglaNegocioException extends LigaException {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}

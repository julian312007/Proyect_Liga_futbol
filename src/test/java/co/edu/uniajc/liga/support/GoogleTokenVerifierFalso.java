package co.edu.uniajc.liga.support;

import co.edu.uniajc.liga.security.GoogleTokenVerifier;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doble de {@link GoogleTokenVerifier} para las pruebas de integración: en vez de consultar a Google,
 * reconoce solo los "ID tokens" que la prueba registró con {@link #registrar} y rechaza cualquier otro con
 * el mismo {@link BadCredentialsException} genérico que lanza el verificador real.
 */
public class GoogleTokenVerifierFalso implements GoogleTokenVerifier {

    private final Map<String, IdentidadGoogle> identidades = new ConcurrentHashMap<>();

    /** Hace que {@code idToken} sea aceptado y represente a {@code identidad}. */
    public void registrar(String idToken, IdentidadGoogle identidad) {
        identidades.put(idToken, identidad);
    }

    @Override
    public IdentidadGoogle verificar(String idToken) {
        IdentidadGoogle identidad = idToken == null ? null : identidades.get(idToken);
        if (identidad == null) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }
        return identidad;
    }
}

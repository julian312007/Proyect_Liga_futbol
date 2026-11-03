package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.security.GoogleTokenVerifier;
import co.edu.uniajc.liga.security.NimbusGoogleTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea el verificador de ID tokens de Google con el ID de cliente de la aplicación.
 *
 * <p>{@code GOOGLE_CLIENT_ID} es obligatorio y no tiene valor por defecto: sin él la aplicación no
 * arranca. Es un identificador público (no un secreto; en este diseño no hay <em>client secret</em>),
 * pero fija la audiencia que se exige a los tokens, así que debe ser el de <em>esta</em> aplicación.
 *
 * <p>Las pruebas sustituyen este bean por un doble ({@code GoogleTokenVerifier} es una interfaz) para no
 * depender de la red ni de un token real de Google.
 */
@Slf4j
@Configuration
public class GoogleAuthConfig {

    /** Prefijo del valor de ejemplo de {@code .env.example}; si sigue puesto, ningún token real será válido. */
    private static final String PREFIJO_VALOR_DE_EJEMPLO = "ejemplo-ficticio";

    @Bean
    GoogleTokenVerifier googleTokenVerifier(@Value("${liga.google.client-id}") String clientId) {
        if (clientId != null && clientId.startsWith(PREFIJO_VALOR_DE_EJEMPLO)) {
            log.warn("GOOGLE_CLIENT_ID conserva el valor de ejemplo de .env.example: el ingreso con Google "
                    + "rechazará todos los tokens hasta que se configure el ID de cliente real.");
        }
        return NimbusGoogleTokenVerifier.conJwkSetUri(NimbusGoogleTokenVerifier.JWK_SET_URI, clientId);
    }
}

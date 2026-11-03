package co.edu.uniajc.liga.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

/**
 * Verificador de ID tokens de Google con el decodificador Nimbus de Spring Security
 * ({@code spring-security-oauth2-jose}); no usa {@code google-api-client}.
 *
 * <p>Un token solo es válido si cumple <strong>todo</strong> lo siguiente:
 * <ul>
 *   <li>Firma RS256 verificable con las claves públicas de Google
 *       ({@value #JWK_SET_URI}), que Nimbus descarga y guarda en caché.</li>
 *   <li>Trae {@code exp} y no ha vencido (tolerancia de 60 s de desfase de reloj).</li>
 *   <li>Emisor ({@code iss}) {@code https://accounts.google.com} o {@code accounts.google.com}.</li>
 *   <li>Audiencia ({@code aud}) que incluye el ID de cliente de esta aplicación ({@code GOOGLE_CLIENT_ID}):
 *       así un token que Google emitió para otra aplicación no sirve aquí.</li>
 *   <li>Trae {@code sub} y {@code email}.</li>
 * </ul>
 * El claim {@code email_verified} no se exige aquí sino en {@code AuthService}, que es quien aplica las
 * reglas; este verificador solo lo informa en {@link IdentidadGoogle#emailVerificado()}.
 *
 * <p>Si un token es inválido se lanza {@link BadCredentialsException} con un mensaje genérico; el motivo
 * concreto solo queda en el log, a nivel {@code DEBUG}. Si Google no responde (no hay conexión, o
 * cambió la URL de las claves) se lanza {@link IllegalStateException}: es una falla del servidor, no del
 * token, y no debe confundirse con un 401.
 */
@Slf4j
public final class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    /** Claves públicas con las que Google firma sus ID tokens. */
    public static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** Google documenta estos dos valores de emisor para los ID tokens. */
    static final List<String> EMISORES_VALIDOS = List.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    NimbusGoogleTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Verificador de producción: descarga las claves de Google y exige la audiencia indicada.
     *
     * @param jwkSetUri URL de las claves públicas ({@link #JWK_SET_URI})
     * @param clientId  ID de cliente de OAuth de esta aplicación ({@code GOOGLE_CLIENT_ID})
     * @throws IllegalStateException si {@code clientId} está vacío
     */
    public static NimbusGoogleTokenVerifier conJwkSetUri(String jwkSetUri, String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validadores(clientId));
        return new NimbusGoogleTokenVerifier(decoder);
    }

    /**
     * Mismo verificador pero con una clave pública fija en vez de la descarga desde Google. Existe para
     * probar la validación real (firma, emisor, audiencia, vigencia) con un par de claves RSA propio.
     */
    static NimbusGoogleTokenVerifier conClavePublica(RSAPublicKey clavePublica, String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(clavePublica)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validadores(clientId));
        return new NimbusGoogleTokenVerifier(decoder);
    }

    /** Las validaciones de emisor, audiencia y vigencia; compartidas por producción y pruebas. */
    static OAuth2TokenValidator<Jwt> validadores(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID no está definido. Cree un ID de cliente de OAuth en Google Cloud Console "
                            + "(Credenciales > ID de cliente de OAuth > Aplicación web) y defínalo como variable de entorno.");
        }
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                // JwtTimestampValidator acepta un token sin exp; un ID token de Google siempre lo trae.
                new JwtClaimValidator<Instant>("exp", exp -> true),
                new JwtClaimValidator<String>("iss", EMISORES_VALIDOS::contains),
                new JwtClaimValidator<List<String>>("aud", audiencia -> audiencia.contains(clientId)));
    }

    @Override
    public IdentidadGoogle verificar(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw credencialesInvalidas();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException ex) {
            // Incluye firma incorrecta, token vencido y emisor o audiencia equivocados. El detalle
            // (por ejemplo "The aud claim is not valid") ayuda a diagnosticar un GOOGLE_CLIENT_ID mal
            // puesto, pero nunca debe llegar al cliente.
            log.debug("ID token de Google rechazado: {}", ex.getMessage());
            throw credencialesInvalidas();
        } catch (JwtException ex) {
            log.error("No fue posible obtener las claves públicas de Google para verificar el ID token: {}",
                    ex.getMessage());
            throw new IllegalStateException("No fue posible verificar el token con Google. Intente de nuevo más tarde.", ex);
        }

        try {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                log.debug("ID token de Google sin sub o sin email (¿faltó el alcance 'email'?)");
                throw credencialesInvalidas();
            }
            boolean emailVerificado = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            return new IdentidadGoogle(sub, email, emailVerificado,
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
        } catch (IllegalArgumentException ex) {
            // Un claim con un tipo imposible de convertir (por ejemplo email_verified: "quizás").
            log.debug("ID token de Google con un claim de tipo inesperado: {}", ex.getMessage());
            throw credencialesInvalidas();
        }
    }

    private static BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
    }
}

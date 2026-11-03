package co.edu.uniajc.liga.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba unitaria del verificador real de ID tokens de Google ({@link NimbusGoogleTokenVerifier}): sin
 * Spring, sin red y sin Docker. Se genera un par de claves RSA en la prueba, se firman ID tokens con la
 * clave privada (con jjwt) y el verificador los valida con la pública, con exactamente las mismas
 * validaciones de emisor, audiencia y vigencia que en producción. Todos los valores son ficticios.
 */
class NimbusGoogleTokenVerifierTest {

    private static final String CLIENT_ID = "cliente-ficticio-de-pruebas.apps.googleusercontent.com";
    private static final String EMISOR = "https://accounts.google.com";

    private static KeyPair claves;
    private static KeyPair clavesAjenas;
    private static NimbusGoogleTokenVerifier verificador;

    @BeforeAll
    static void generarClaves() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        claves = generador.generateKeyPair();
        clavesAjenas = generador.generateKeyPair();
        verificador = NimbusGoogleTokenVerifier.conClavePublica((RSAPublicKey) claves.getPublic(), CLIENT_ID);
    }

    // ============================================================ Token válido

    @Test
    @DisplayName("ID token válido: devuelve la identidad (el correo se normaliza a minúsculas)")
    void tokenValido_devuelveLaIdentidad() {
        IdentidadGoogle identidad = verificador.verificar(firmar(tokenBase()
                .issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora())));

        assertThat(identidad.sub()).isEqualTo("sub-123");
        assertThat(identidad.email()).isEqualTo("ana@example.com");
        assertThat(identidad.emailVerificado()).isTrue();
        assertThat(identidad.nombre()).isEqualTo("Ana Pérez");
        assertThat(identidad.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/foto-ficticia");
    }

    @Test
    @DisplayName("Emisor 'accounts.google.com' (sin esquema), el otro valor que documenta Google: también es válido")
    void emisorSinEsquema_esValido() {
        IdentidadGoogle identidad = verificador.verificar(firmar(tokenBase()
                .issuer("accounts.google.com").audience().add(CLIENT_ID).and().expiration(enUnaHora())));

        assertThat(identidad.sub()).isEqualTo("sub-123");
    }

    @Test
    @DisplayName("Audiencia con varios valores que incluye el ID de cliente: es válido")
    void audienciaConVariosValoresQueIncluyeElClienteEsValida() {
        IdentidadGoogle identidad = verificador.verificar(firmar(tokenBase()
                .issuer(EMISOR).audience().add("otra-aplicacion").add(CLIENT_ID).and().expiration(enUnaHora())));

        assertThat(identidad.sub()).isEqualTo("sub-123");
    }

    @Test
    @DisplayName("Sin 'email_verified' o en false: el verificador lo informa (false); rechazarlo es regla del servicio")
    void emailNoVerificado_seInformaComoFalse() {
        String sinClaim = firmar(sinClaims("email_verified").issuer(EMISOR)
                .audience().add(CLIENT_ID).and().expiration(enUnaHora()));
        String enFalse = firmar(tokenBase().claim("email_verified", false).issuer(EMISOR)
                .audience().add(CLIENT_ID).and().expiration(enUnaHora()));

        assertThat(verificador.verificar(sinClaim).emailVerificado()).isFalse();
        assertThat(verificador.verificar(enFalse).emailVerificado()).isFalse();
    }

    // ============================================================== Emisor

    @ParameterizedTest(name = "emisor \"{0}\"")
    @ValueSource(strings = {"https://evil.example.com", "https://accounts.google.com.evil.example.com",
            "accounts.google.com.evil.example.com", "http://accounts.google.com"})
    @DisplayName("Emisor distinto de Google: se rechaza")
    void emisorIncorrecto_seRechaza(String emisor) {
        String token = firmar(tokenBase().issuer(emisor).audience().add(CLIENT_ID).and().expiration(enUnaHora()));

        assertRechazado(token);
    }

    @Test
    @DisplayName("Token sin emisor: se rechaza")
    void sinEmisor_seRechaza() {
        assertRechazado(firmar(tokenBase().audience().add(CLIENT_ID).and().expiration(enUnaHora())));
    }

    // ============================================================ Audiencia

    @ParameterizedTest(name = "audiencia \"{0}\"")
    @ValueSource(strings = {"otra-aplicacion.apps.googleusercontent.com", "cliente-ficticio-de-pruebas",
            "CLIENTE-FICTICIO-DE-PRUEBAS.APPS.GOOGLEUSERCONTENT.COM"})
    @DisplayName("Audiencia de otra aplicación: se rechaza (un token que Google emitió para otra app no sirve aquí)")
    void audienciaIncorrecta_seRechaza(String audiencia) {
        String token = firmar(tokenBase().issuer(EMISOR).audience().add(audiencia).and().expiration(enUnaHora()));

        assertRechazado(token);
    }

    @Test
    @DisplayName("Token sin audiencia: se rechaza")
    void sinAudiencia_seRechaza() {
        assertRechazado(firmar(tokenBase().issuer(EMISOR).expiration(enUnaHora())));
    }

    // ============================================================= Vigencia

    @Test
    @DisplayName("Token vencido hace una hora: se rechaza")
    void tokenVencido_seRechaza() {
        String token = firmar(tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and()
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS))));

        assertRechazado(token);
    }

    @Test
    @DisplayName("Token sin fecha de expiración: se rechaza")
    void sinExpiracion_seRechaza() {
        assertRechazado(firmar(tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and()));
    }

    // =============================================================== Firma

    @Test
    @DisplayName("Token firmado con otra clave RSA: se rechaza")
    void firmadoConOtraClave_seRechaza() {
        String token = tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora())
                .signWith(clavesAjenas.getPrivate(), Jwts.SIG.RS256).compact();

        assertRechazado(token);
    }

    @Test
    @DisplayName("Token con la firma alterada: se rechaza")
    void firmaAlterada_seRechaza() {
        String valido = firmar(tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora()));
        int posicion = valido.lastIndexOf('.') + 10;
        char distinto = valido.charAt(posicion) == 'A' ? 'B' : 'A';
        String alterado = valido.substring(0, posicion) + distinto + valido.substring(posicion + 1);

        assertRechazado(alterado);
    }

    @Test
    @DisplayName("Token HS256 (firma simétrica): se rechaza, solo se acepta RS256")
    void tokenHs256_seRechaza() {
        SecretKeySpec secreto = new SecretKeySpec(
                "un-secreto-ficticio-simetrico-de-mas-de-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token = tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora())
                .signWith(secreto, Jwts.SIG.HS256).compact();

        assertRechazado(token);
    }

    @Test
    @DisplayName("Token sin firma (alg none): se rechaza")
    void tokenSinFirma_seRechaza() {
        String token = tokenBase().issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora()).compact();

        assertRechazado(token);
    }

    // ================================================ Claims obligatorios y basura

    @Test
    @DisplayName("Token sin email: se rechaza")
    void sinEmail_seRechaza() {
        assertRechazado(firmar(sinClaims("email").issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora())));
    }

    @Test
    @DisplayName("Token sin sub: se rechaza")
    void sinSub_seRechaza() {
        JwtBuilder sinSub = Jwts.builder()
                .claim("email", "ana@example.com").claim("email_verified", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .issuer(EMISOR).audience().add(CLIENT_ID).and().expiration(enUnaHora());

        assertRechazado(firmar(sinSub));
    }

    @ParameterizedTest(name = "token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "a.b.c", "....", "eyJhbGciOiJSUzI1NiJ9.e30.firma-falsa"})
    @DisplayName("Texto que no es un JWT (nulo, vacío o basura): se rechaza sin lanzar otra excepción")
    void textoQueNoEsUnJwt_seRechaza(String basura) {
        assertRechazado(basura);
    }

    @Test
    @DisplayName("La excepción es siempre genérica: no revela el motivo ni incluye el token")
    void laExcepcionEsGenerica() {
        String token = firmar(tokenBase().issuer("https://evil.example.com")
                .audience().add("otra-aplicacion").and().expiration(enUnaHora()));

        assertThatThrownBy(() -> verificador.verificar(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS)
                .hasMessageNotContaining("evil")
                .hasMessageNotContaining(token)
                .hasNoCause();
    }

    // ================================================== Configuración e infraestructura

    @Test
    @DisplayName("Si no se pudo consultar a Google (falla de red): IllegalStateException, no un 401")
    void fallaDeInfraestructura_noEsUnaFallaDeCredenciales() {
        NimbusGoogleTokenVerifier sinRed = new NimbusGoogleTokenVerifier(token -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        });

        assertThatThrownBy(() -> sinRed.verificar("cualquier.token.aqui"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest(name = "GOOGLE_CLIENT_ID \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("GOOGLE_CLIENT_ID vacío: la aplicación no arranca, con un mensaje que dice qué falta")
    void clientIdVacio_fallaAlConstruir(String clientIdVacio) {
        assertThatThrownBy(() -> NimbusGoogleTokenVerifier.conJwkSetUri(
                NimbusGoogleTokenVerifier.JWK_SET_URI, clientIdVacio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
    }

    // ===================================================================== Apoyo

    /** Un ID token típico de Google sin emisor, audiencia ni expiración: cada prueba agrega lo que necesita. */
    private static JwtBuilder tokenBase() {
        return Jwts.builder()
                .subject("sub-123")
                .claim("email", "Ana@Example.com")
                .claim("email_verified", true)
                .claim("name", "Ana Pérez")
                .claim("picture", "https://lh3.googleusercontent.com/a/foto-ficticia")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)));
    }

    /** Igual que {@link #tokenBase()} pero sin el claim indicado. */
    private static JwtBuilder sinClaims(String claimAOmitir) {
        JwtBuilder builder = Jwts.builder()
                .subject("sub-123")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)));
        if (!"email".equals(claimAOmitir)) {
            builder.claim("email", "ana@example.com");
        }
        if (!"email_verified".equals(claimAOmitir)) {
            builder.claim("email_verified", true);
        }
        return builder;
    }

    private static String firmar(JwtBuilder builder) {
        return builder.signWith(claves.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static Date enUnaHora() {
        return Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static void assertRechazado(String token) {
        assertThatThrownBy(() -> verificador.verificar(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS);
    }
}

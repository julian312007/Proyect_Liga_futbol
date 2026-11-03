package co.edu.uniajc.liga.security;

import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.support.TokensDePrueba;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba unitaria de {@link JwtService}: sin Spring ni Docker. Los secretos son ficticios.
 */
class JwtServiceTest {

    /** 64 bytes: sirve también para firmar con HS512 en la prueba del algoritmo no permitido. */
    private static final String SECRETO = "secreto-ficticio-solo-para-pruebas-unitarias-0123456789-abcdefghij";

    private JwtService jwtService;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(SECRETO, 60);
    }

    // ------------------------------------------------------- Arranque (secreto y vigencia)

    @Test
    @DisplayName("Secreto de 31 bytes: la aplicación no arranca (mínimo 32)")
    void constructor_conSecretoDe31Bytes_falla() {
        String secretoCorto = "a".repeat(31);

        assertThatThrownBy(() -> new JwtService(secretoCorto, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demasiado corto")
                .hasMessageContaining("31")
                // El mensaje nunca debe incluir el secreto.
                .hasMessageNotContaining(secretoCorto);
    }

    @Test
    @DisplayName("Secreto de exactamente 32 bytes: es válido")
    void constructor_conSecretoDe32Bytes_funciona() {
        JwtService servicio = new JwtService("a".repeat(32), 60);

        assertThat(servicio.generarToken("ana@example.com", Rol.USER)).isNotBlank();
    }

    @ParameterizedTest(name = "secreto \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Secreto vacío o en blanco: la aplicación no arranca")
    void constructor_conSecretoVacio_falla(String secretoVacio) {
        assertThatThrownBy(() -> new JwtService(secretoVacio, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Secreto nulo: la aplicación no arranca")
    void constructor_conSecretoNulo_falla() {
        assertThatThrownBy(() -> new JwtService(null, 60))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "vigencia {0} minutos")
    @ValueSource(longs = {0, -5})
    @DisplayName("Vigencia cero o negativa: la aplicación no arranca")
    void constructor_conVigenciaNoPositiva_falla(long minutos) {
        assertThatThrownBy(() -> new JwtService(SECRETO, minutos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_EXPIRATION_MINUTES");
    }

    // ----------------------------------------------------------------------- Emisión

    @Test
    @DisplayName("El token lleva los claims sub, rol, iat y exp, y la vigencia configurada")
    void generarToken_incluyeSubRolIatYExp() {
        String token = jwtService.generarToken("ana@example.com", Rol.ADMIN);

        Claims claims = leer(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("ana@example.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        long vigenciaSegundos = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(vigenciaSegundos).isEqualTo(60 * 60);
        assertThat(jwtService.getVigenciaSegundos()).isEqualTo(3600);
    }

    @Test
    @DisplayName("El token se firma con HS256 aunque el secreto mida 64 bytes (jjwt elegiría HS512)")
    void generarToken_usaHs256() {
        String token = jwtService.generarToken("ana@example.com", Rol.USER);

        assertThat(leer(token).getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    // --------------------------------------------------------------- Validación: positivo

    @Test
    @DisplayName("Token recién emitido: es válido y devuelve el sub (el correo)")
    void validar_tokenCorrecto_devuelveElUsername() {
        String token = jwtService.generarToken("ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(token)).contains("ana@example.com");
    }

    // --------------------------------------------------------------- Validación: negativos

    @Test
    @DisplayName("Token vencido: no es válido")
    void validar_tokenVencido_devuelveVacio() {
        String token = TokensDePrueba.vencido(SECRETO, "ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(token)).isEmpty();
    }

    @Test
    @DisplayName("Token firmado con otra clave: no es válido")
    void validar_firmaDeOtraClave_devuelveVacio() {
        String token = TokensDePrueba.firmadoConOtraClave("ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(token)).isEmpty();
    }

    @Test
    @DisplayName("Payload editado (USER -> ADMIN) con la firma original: no es válido")
    void validar_payloadManipulado_devuelveVacio() {
        String original = jwtService.generarToken("ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(TokensDePrueba.conPayloadManipulado(original))).isEmpty();
    }

    @Test
    @DisplayName("Firma alterada: no es válido")
    void validar_firmaAlterada_devuelveVacio() {
        String original = jwtService.generarToken("ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(TokensDePrueba.conFirmaAlterada(original))).isEmpty();
    }

    @Test
    @DisplayName("Token sin firma (alg none): no es válido")
    void validar_tokenSinFirma_devuelveVacio() {
        String token = TokensDePrueba.sinFirma("ana@example.com", Rol.ADMIN);

        assertThat(jwtService.validarYObtenerSubject(token)).isEmpty();
    }

    @Test
    @DisplayName("Token HS512 firmado con la misma clave: no es válido (solo se acepta HS256)")
    void validar_algoritmoDistintoDeHs256_devuelveVacio() {
        String token = TokensDePrueba.conHs512(SECRETO, "ana@example.com", Rol.USER);

        assertThat(jwtService.validarYObtenerSubject(token)).isEmpty();
    }

    @Test
    @DisplayName("Token RS256 como los ID tokens de Google: no es válido (el JWT propio es solo HS256)")
    void validar_tokenRs256_devuelveVacio() throws Exception {
        java.security.KeyPairGenerator generador = java.security.KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        String comoUnIdTokenDeGoogle = Jwts.builder()
                .subject("ana@example.com")
                .issuer("https://accounts.google.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(generador.generateKeyPair().getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThat(jwtService.validarYObtenerSubject(comoUnIdTokenDeGoogle)).isEmpty();
    }

    @Test
    @DisplayName("Token firmado correctamente pero sin sub: no es válido")
    void validar_tokenSinSubject_devuelveVacio() {
        String sinSub = Jwts.builder()
                .claim("rol", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.validarYObtenerSubject(sinSub)).isEmpty();
    }

    @ParameterizedTest(name = "token \"{0}\"")
    @ValueSource(strings = {"", "   ", "abc", "a.b.c", "....", "eyJhbGciOiJIUzI1NiJ9.e30.firma-falsa"})
    @DisplayName("Texto que no es un JWT: no es válido y no lanza excepciones")
    void validar_textoMalFormado_devuelveVacio(String basura) {
        assertThat(jwtService.validarYObtenerSubject(basura)).isEmpty();
    }

    @Test
    @DisplayName("Token nulo: no es válido y no lanza excepciones")
    void validar_tokenNulo_devuelveVacio() {
        assertThat(jwtService.validarYObtenerSubject(null)).isEmpty();
    }

    private Jws<Claims> leer(String token) {
        return Jwts.parser()
                .verifyWith(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .parseSignedClaims(token);
    }
}

package co.edu.uniajc.liga.support;

import co.edu.uniajc.liga.entity.Rol;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Fábrica de tokens defectuosos para las pruebas de seguridad (vencido, manipulado, de otra clave...).
 * Usa jjwt directamente, sin pasar por {@code JwtService}, para poder fabricar lo que el servicio
 * jamás emitiría. Todos los secretos que aparecen aquí son ficticios.
 */
public final class TokensDePrueba {

    /** Clave distinta de la de la aplicación, para simular un token firmado por un tercero. */
    private static final String SECRETO_AJENO = "otro-secreto-ficticio-distinto-al-de-la-aplicacion-0123456789";

    private TokensDePrueba() {
    }

    /** Token firmado correctamente con {@code secreto} pero cuyo {@code exp} ya pasó hace una hora. */
    public static String vencido(String secreto, String subject, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol.name())
                .issuedAt(Date.from(ahora.minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(ahora.minus(1, ChronoUnit.HOURS)))
                .signWith(clave(secreto), Jwts.SIG.HS256)
                .compact();
    }

    /** Token vigente pero firmado con una clave que no es la de la aplicación. */
    public static String firmadoConOtraClave(String subject, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(1, ChronoUnit.HOURS)))
                .signWith(clave(SECRETO_AJENO), Jwts.SIG.HS256)
                .compact();
    }

    /** Token vigente, firmado con HS512 y la clave de la aplicación (algoritmo que no se acepta). */
    public static String conHs512(String secretoDe64Bytes, String subject, Rol rol) {
        Instant ahora = Instant.now();
        SecretKey claveHs512 = new SecretKeySpec(secretoDe64Bytes.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(1, ChronoUnit.HOURS)))
                .signWith(claveHs512, Jwts.SIG.HS512)
                .compact();
    }

    /** Token vigente sin firma (algoritmo "none"): lo que enviaría un atacante que prueba suerte. */
    public static String sinFirma(String subject, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(1, ChronoUnit.HOURS)))
                .compact();
    }

    /**
     * Cambia el rol dentro del payload (USER por ADMIN o al revés) y conserva la firma original:
     * simula un intento de escalar privilegios editando el token.
     */
    public static String conPayloadManipulado(String tokenValido) {
        String[] partes = tokenValido.split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String modificado = json.contains("\"USER\"")
                ? json.replace("\"USER\"", "\"ADMIN\"")
                : json.replace("\"ADMIN\"", "\"USER\"");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modificado.getBytes(StandardCharsets.UTF_8));
        return partes[0] + "." + payload + "." + partes[2];
    }

    /** Altera un carácter del medio de la firma (el último solo aporta bits de relleno en Base64). */
    public static String conFirmaAlterada(String tokenValido) {
        int inicioFirma = tokenValido.lastIndexOf('.') + 1;
        int posicion = inicioFirma + 5;
        char original = tokenValido.charAt(posicion);
        char distinto = original == 'A' ? 'B' : 'A';
        return tokenValido.substring(0, posicion) + distinto + tokenValido.substring(posicion + 1);
    }

    private static SecretKey clave(String secreto) {
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}

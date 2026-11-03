package co.edu.uniajc.liga.security;

import co.edu.uniajc.liga.entity.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite y valida los JWT de la API (firma HS256 con jjwt).
 *
 * <p>Claims del token: {@code sub} (el correo del usuario), {@code rol}, {@code iat} y {@code exp}. El secreto viene
 * de {@code JWT_SECRET} y la vigencia de {@code JWT_EXPIRATION_MINUTES} (60 por defecto), ambos por
 * variable de entorno. Sin <em>refresh token</em> ni lista de revocación: un token robado sirve hasta
 * que venza, por eso la vigencia debe ser corta.
 *
 * <p>El secreto se usa como texto (sus bytes UTF-8), no como Base64. Si es demasiado corto, el
 * constructor lanza una excepción y la aplicación no arranca (falla rápido).
 *
 * <p>La validación nunca lanza excepciones al llamador: cualquier token vencido, manipulado, mal
 * formado, sin firma o firmado con otro algoritmo se trata como "no válido" ({@link Optional#empty()}).
 */
@Slf4j
@Service
public class JwtService {

    /** Mínimo que exige RFC 7518 para HS256: 256 bits. */
    static final int LONGITUD_MINIMA_SECRETO_BYTES = 32;

    /** Nombre del claim con el rol. */
    static final String CLAIM_ROL = "rol";

    /** Único algoritmo aceptado. Se comprueba explícitamente al validar (ver {@link #validarYObtenerSubject}). */
    private static final String ALGORITMO = "HS256";

    private final SecretKey clave;
    private final Duration vigencia;
    private final JwtParser parser;

    /**
     * @param secreto         valor de {@code liga.jwt.secret} (variable {@code JWT_SECRET}); mínimo 32 bytes
     * @param vigenciaMinutos valor de {@code liga.jwt.expiration-minutes} (variable
     *                        {@code JWT_EXPIRATION_MINUTES}); mayor que cero
     * @throws IllegalStateException si el secreto falta o es demasiado corto, o la vigencia no es positiva
     */
    public JwtService(@Value("${liga.jwt.secret}") String secreto,
                      @Value("${liga.jwt.expiration-minutes:60}") long vigenciaMinutos) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET no está definido. Genere uno aleatorio de al menos 32 caracteres "
                            + "(por ejemplo: openssl rand -base64 48) y defínalo como variable de entorno.");
        }
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_SECRETO_BYTES) {
            // Solo se informa la longitud, nunca el valor.
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corto: tiene %d bytes y HS256 exige al menos %d (256 bits)."
                            .formatted(bytes.length, LONGITUD_MINIMA_SECRETO_BYTES));
        }
        if (vigenciaMinutos <= 0) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_MINUTES debe ser mayor que cero (valor recibido: %d)."
                            .formatted(vigenciaMinutos));
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
        // El parser es inmutable y seguro entre hilos: se construye una sola vez.
        this.parser = Jwts.parser().verifyWith(clave).build();
    }

    /**
     * Emite un token para el usuario.
     *
     * @param subject correo del usuario, ya normalizado (será el claim {@code sub})
     * @param rol      rol del usuario (claim {@code rol})
     * @return el JWT compacto
     */
    public String generarToken(String subject, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ROL, rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(vigencia)))
                // Algoritmo fijado a HS256. Sin él, jjwt elegiría HS384 o HS512 según el largo del secreto.
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida el token y devuelve su {@code sub} (el correo del usuario).
     *
     * @return el {@code sub} si la firma, la vigencia y el algoritmo son correctos; vacío en cualquier
     * otro caso (token nulo, mal formado, manipulado, vencido, sin firma o con otro algoritmo)
     */
    public Optional<String> validarYObtenerSubject(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            // Defensa en profundidad: aunque la firma sea válida, solo se acepta HS256.
            if (!ALGORITMO.equals(jws.getHeader().getAlgorithm())) {
                log.debug("Token JWT rechazado: algoritmo no permitido");
                return Optional.empty();
            }
            String subject = jws.getPayload().getSubject();
            return subject == null || subject.isBlank() ? Optional.empty() : Optional.of(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            // Solo se registra el tipo de fallo (y a nivel debug): ni el token ni el mensaje, que podría citarlo.
            log.debug("Token JWT rechazado: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Segundos de vigencia de los tokens nuevos; es el valor de {@code expiraEn} en el ingreso con Google. */
    public long getVigenciaSegundos() {
        return vigencia.toSeconds();
    }
}

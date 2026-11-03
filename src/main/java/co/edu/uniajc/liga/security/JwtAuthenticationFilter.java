package co.edu.uniajc.liga.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica cada solicitud que trae {@code Authorization: Bearer <token>}.
 *
 * <p>Si el token es válido y el usuario existe y está activo, deja la autenticación en el
 * {@code SecurityContext}. En cualquier otro caso (sin encabezado, token inválido o vencido, usuario
 * borrado o desactivado) <strong>no hace nada y deja pasar la solicitud como anónima</strong>: es la
 * regla {@code authenticated()} de {@code SecurityConfig} la que responde {@code 401} en las rutas
 * protegidas. Así un token vencido tampoco bloquea la ruta pública de ingreso con Google.
 *
 * <p>El usuario se consulta en la base en cada solicitud (una búsqueda por el índice único de
 * {@code email}, que es el {@code sub} del token). A cambio, desactivar a un usuario o cambiarle el rol surte efecto de inmediato en
 * vez de esperar a que venza el token; el claim {@code rol} del token es informativo para el cliente.
 *
 * <p>No es un bean a propósito: {@code SecurityConfig} lo crea y lo agrega a la cadena. Si fuera un
 * bean, Spring Boot lo registraría además como filtro global del servlet.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extraerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.validarYObtenerSubject(token).ifPresent(subject -> autenticar(subject, request));
        }
        filterChain.doFilter(request, response);
    }

    /** Devuelve el token del encabezado {@code Authorization: Bearer ...}, o {@code null} si no hay. */
    private static String extraerToken(HttpServletRequest request) {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            return null;
        }
        String token = encabezado.substring(PREFIJO_BEARER.length()).strip();
        return token.isEmpty() ? null : token;
    }

    private void autenticar(String subject, HttpServletRequest request) {
        try {
            UserDetails usuario = userDetailsService.loadUserByUsername(subject);
            if (!usuario.isEnabled()) {
                log.debug("Token válido de un usuario desactivado: se ignora");
                return;
            }
            UsernamePasswordAuthenticationToken autenticacion =
                    UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // Contexto nuevo en vez de modificar el existente, como recomienda Spring Security 6+.
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
        } catch (UsernameNotFoundException ex) {
            log.debug("Token válido de un usuario que ya no existe: se ignora");
        }
    }
}

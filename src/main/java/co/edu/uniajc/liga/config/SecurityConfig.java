package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.security.JwtAuthenticationFilter;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.security.RestAccessDeniedHandler;
import co.edu.uniajc.liga.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de seguridad de la API: JWT propio sin sesión, ingreso con Google y roles
 * {@code ADMIN} y {@code USER}.
 *
 * <p>Reglas de acceso en dos niveles, sin duplicarse:
 * <ul>
 *   <li><b>Autenticación</b> (quién eres): aquí. Son públicos solo {@code POST /api/v1/auth/google},
 *       Swagger/OpenAPI y {@code /error}; todo lo demás exige un token propio válido, y si falta o es
 *       inválido la respuesta es {@code 401} con formato {@code ApiError}.</li>
 *   <li><b>Autorización</b> (qué rol necesitas): con {@code @PreAuthorize} en los métodos de los
 *       controladores, habilitado por {@code @EnableMethodSecurity}. Un rol insuficiente es {@code 403}.</li>
 * </ul>
 *
 * <p>No hay contraseñas: la identidad la prueba Google ({@code POST /api/v1/auth/google}) y el resto de
 * solicitudes llevan el JWT propio de la API. Por eso no existen aquí un {@code PasswordEncoder} ni un
 * {@code AuthenticationManager}.
 *
 * <p>Cierre de sesión: no hay endpoint. El token no se puede revocar; el cliente lo descarta y este
 * deja de usarse cuando vence.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Rutas públicas que solo se abren con el método POST. */
    private static final String[] RUTAS_PUBLICAS_POST = {
            "/api/v1/auth/google"
    };

    /**
     * Rutas accesibles sin token con cualquier método: documentación de la API, la página de error de Spring y la
     * página estática con el botón de Google (necesaria para obtener el ID token antes de tener un JWT propio).
     */
    private static final String[] RUTAS_PUBLICAS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/login-google.html",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            UserDetailsService userDetailsService,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF se deshabilita SOLO porque la API es sin estado: no usa sesión ni cookies de
                // autenticación (el token viaja en el encabezado Authorization, que un sitio malicioso
                // no puede añadir a una solicitud de otro origen). Si algún día se agregaran sesiones o
                // cookies de autenticación, este ajuste debe revisarse.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // La autenticación es solo por JWT: se apagan los mecanismos que Spring Security trae de serie.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, RUTAS_PUBLICAS_POST).permitAll()
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

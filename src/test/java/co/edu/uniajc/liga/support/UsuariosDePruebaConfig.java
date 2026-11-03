package co.edu.uniajc.liga.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * {@link UserDetailsService} en memoria para las pruebas {@code @WebMvcTest}, que no tienen base de
 * datos. Sustituye a {@code UserDetailsServiceImpl} con tres usuarios fijos; el "username" de Spring
 * Security es el correo, igual que en la aplicación (es el {@code sub} del JWT propio):
 * <ul>
 *   <li>{@code ana@example.com}: rol USER</li>
 *   <li>{@code root@example.com}: rol ADMIN</li>
 *   <li>{@code inactivo@example.com}: rol USER, pero deshabilitado</li>
 * </ul>
 * Las contraseñas no existen en este diseño (las pruebas se autentican con tokens), por eso son un valor
 * ficticio sin uso.
 */
@TestConfiguration(proxyBeanMethods = false)
public class UsuariosDePruebaConfig {

    public static final String ANA = "ana@example.com";
    public static final String ROOT = "root@example.com";
    public static final String INACTIVO = "inactivo@example.com";

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername(ANA).password("{noop}no-se-usa").roles("USER").build(),
                User.withUsername(ROOT).password("{noop}no-se-usa").roles("ADMIN").build(),
                User.withUsername(INACTIVO).password("{noop}no-se-usa").roles("USER").disabled(true).build());
    }
}

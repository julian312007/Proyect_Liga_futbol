package co.edu.uniajc.liga.security;

import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.support.TokensDePrueba;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link JwtAuthenticationFilter}: sin Spring ni Docker. Verifica que solo un
 * token válido de un usuario existente y activo deja una autenticación en el contexto, y que en
 * todos los demás casos la solicitud sigue su curso como anónima (la respuesta 401 la da después
 * la regla {@code authenticated()}, que se prueba en {@code SecurityConfigTest}).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRETO = "secreto-ficticio-para-el-filtro-jwt-solo-pruebas-0123456789";

    private final JwtService jwtService = new JwtService(SECRETO, 60);

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filtro;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        filtro = new JwtAuthenticationFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Token válido de un usuario activo: queda autenticado con su rol y la solicitud continúa")
    void tokenValido_autenticaAlUsuario() throws Exception {
        when(userDetailsService.loadUserByUsername("ana@example.com"))
                .thenReturn(User.withUsername("ana@example.com").password("x").roles("USER").build());
        request.addHeader("Authorization", "Bearer " + jwtService.generarToken("ana@example.com", Rol.USER));

        filtro.doFilter(request, response, filterChain);

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion).isNotNull();
        assertThat(autenticacion.isAuthenticated()).isTrue();
        assertThat(autenticacion.getName()).isEqualTo("ana@example.com");
        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("El esquema Bearer no distingue mayúsculas")
    void esquemaBearer_noDistingueMayusculas() throws Exception {
        when(userDetailsService.loadUserByUsername("ana@example.com"))
                .thenReturn(User.withUsername("ana@example.com").password("x").roles("USER").build());
        request.addHeader("Authorization", "bearer " + jwtService.generarToken("ana@example.com", Rol.USER));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("El rol sale de la base de datos y no del claim: un token con rol ADMIN de un USER sigue siendo USER")
    void elRolSaleDeLaBaseDeDatos() throws Exception {
        when(userDetailsService.loadUserByUsername("ana@example.com"))
                .thenReturn(User.withUsername("ana@example.com").password("x").roles("USER").build());
        // Token legítimo (firmado por la aplicación) cuyo claim dice ADMIN pero el usuario real es USER.
        request.addHeader("Authorization", "Bearer " + jwtService.generarToken("ana@example.com", Rol.ADMIN));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Sin encabezado Authorization: no autentica y la solicitud continúa")
    void sinEncabezado_noAutentica() throws Exception {
        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Esquema distinto de Bearer (Basic): no autentica")
    void esquemaDistinto_noAutentica() throws Exception {
        request.addHeader("Authorization", "Basic YW5hOmNsYXZl");

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("'Bearer ' sin token: no autentica")
    void bearerSinToken_noAutentica() throws Exception {
        request.addHeader("Authorization", "Bearer ");

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Token vencido: no autentica y ni siquiera consulta la base de datos")
    void tokenVencido_noAutentica() throws Exception {
        request.addHeader("Authorization", "Bearer " + TokensDePrueba.vencido(SECRETO, "ana@example.com", Rol.USER));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Token manipulado: no autentica")
    void tokenManipulado_noAutentica() throws Exception {
        String valido = jwtService.generarToken("ana@example.com", Rol.USER);
        request.addHeader("Authorization", "Bearer " + TokensDePrueba.conPayloadManipulado(valido));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Token válido de un usuario que ya no existe: no autentica")
    void usuarioInexistente_noAutentica() throws Exception {
        when(userDetailsService.loadUserByUsername("fantasma@example.com"))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado"));
        request.addHeader("Authorization", "Bearer " + jwtService.generarToken("fantasma@example.com", Rol.USER));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token válido de un usuario desactivado: no autentica")
    void usuarioDesactivado_noAutentica() throws Exception {
        when(userDetailsService.loadUserByUsername("ana@example.com"))
                .thenReturn(User.withUsername("ana@example.com").password("x").roles("USER").disabled(true).build());
        request.addHeader("Authorization", "Bearer " + jwtService.generarToken("ana@example.com", Rol.USER));

        filtro.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}

package co.edu.uniajc.liga.security;

import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puente entre {@link JwtAuthenticationFilter} y la tabla {@code usuario}: dado el {@code sub} de un
 * token propio (el correo del usuario), devuelve sus datos de seguridad. Spring Security llama
 * "username" a ese valor, pero aquí es el correo.
 *
 * <p>El rol se traduce a la autoridad {@code ROLE_<rol>} (lo que espera {@code hasRole('ADMIN')}) y un
 * usuario con {@code activo = false} se devuelve deshabilitado.
 *
 * <p>No hay contraseñas en este diseño. Spring exige un valor, así que se entrega uno que no coincide con
 * ninguna contraseña posible, por si alguien agregara después un mecanismo basado en contraseñas por
 * descuido. Tener este bean también evita que Spring Boot genere un usuario de memoria con una
 * contraseña aleatoria que se imprime en el registro de arranque.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    /** Con el prefijo {@code {bcrypt}} pero sin ser un hash BCrypt válido: nunca coincide con nada. */
    private static final String SIN_CONTRASENA = "{bcrypt}!";

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(usuario.getEmail())
                .password(SIN_CONTRASENA)
                .roles(usuario.getRol().name())
                .disabled(!usuario.isActivo())
                .build();
    }
}

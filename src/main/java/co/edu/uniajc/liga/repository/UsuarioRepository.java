package co.edu.uniajc.liga.repository;

import co.edu.uniajc.liga.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a la tabla {@code usuario}. Solo lo usan los servicios y {@code UserDetailsServiceImpl}.
 *
 * <p>El correo se busca ya normalizado (minúsculas, sin espacios), tal como se guarda.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Búsqueda principal al ingresar con Google: el {@code sub} identifica la cuenta de forma estable. */
    Optional<Usuario> findByGoogleSub(String googleSub);

    Optional<Usuario> findByEmail(String email);

    /** Para sincronizar un cambio de correo de Google: ¿lo usa otro usuario distinto del indicado? */
    boolean existsByEmailAndIdNot(String email, Long id);
}

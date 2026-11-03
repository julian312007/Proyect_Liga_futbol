package co.edu.uniajc.liga.support;

import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.infra.TestcontainersConfig;
import co.edu.uniajc.liga.repository.UsuarioRepository;
import co.edu.uniajc.liga.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Base de las pruebas de integración de negocio (Fases 2 a 6). Requiere Docker; corre en {@code mvn verify}.
 *
 * <p><b>Mantén exactamente estas anotaciones</b> (incluida la propiedad {@code liga.admin.email}) en todas
 * las clases {@code *IT} de negocio: Spring reutiliza el contexto cacheado cuando la configuración es idéntica
 * y arrancar un contexto con PostgreSQL de Testcontainers cuesta de 25 s a varios minutos en esta máquina.
 * Es la misma configuración que usa {@code AuthFlujoIT}.
 *
 * <p>Todas las pruebas comparten la misma base de datos: usa datos propios con nombres únicos
 * ({@link #unico(String)}) y no asumas que las tablas están vacías ni compares totales globales.
 */
@SpringBootTest(properties = "liga.admin.email=admin.it@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, GoogleVerificadorFalsoConfig.class})
public abstract class BaseIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    /** Cabecera {@code Authorization} de un ADMIN real (guardado en la base de datos), lista para usar. */
    protected String authAdmin() {
        return "Bearer " + tokenDe(Rol.ADMIN);
    }

    /** Cabecera {@code Authorization} de un USER real (guardado en la base de datos), lista para usar. */
    protected String authUser() {
        return "Bearer " + tokenDe(Rol.USER);
    }

    /** {@code base} más un sufijo aleatorio corto, para no chocar con los datos de otras pruebas. */
    protected static String unico(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String tokenDe(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail("it-" + UUID.randomUUID() + "@example.com");
        usuario.setNombre("Usuario de prueba " + rol.name());
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        // El sub del JWT propio es el correo del usuario.
        return jwtService.generarToken(usuario.getEmail(), rol);
    }
}

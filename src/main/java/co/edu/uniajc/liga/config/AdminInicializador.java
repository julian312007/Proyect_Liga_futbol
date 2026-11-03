package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Crea el administrador inicial al arrancar, si no existe. Es idempotente: se puede ejecutar en cada
 * arranque sin duplicar ni modificar nada.
 *
 * <p>Ya no hay contraseña: solo se necesita el correo de Google del administrador, que llega por la
 * variable de entorno {@code LIGA_ADMIN_EMAIL}. Se crea un usuario {@code ADMIN} con ese correo y sin
 * {@code google_sub}; la primera vez que esa persona ingrese con Google, el sistema enlaza su cuenta con
 * esta fila y conserva el rol (ver el riesgo del enlace por correo en {@code AuthServiceImpl}).
 * Comportamiento:
 * <ul>
 *   <li>Variable vacía: no se crea nada y se deja un aviso en el log (útil en pruebas y en CI).</li>
 *   <li>Variable con algo que no parece un correo: la aplicación no arranca.</li>
 *   <li>Correo válido: se crea con rol {@code ADMIN} si no existe un usuario con ese correo.</li>
 * </ul>
 * La regla de crear el usuario vive en {@link AuthService}; esta clase solo lee la configuración.
 */
@Slf4j
@Component
public class AdminInicializador implements ApplicationRunner {

    /** Comprobación mínima de forma (algo@dominio.tld) para fallar pronto ante un error de tipeo. */
    private static final Pattern FORMA_DE_CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final AuthService authService;
    private final String email;

    public AdminInicializador(AuthService authService, @Value("${liga.admin.email:}") String email) {
        this.authService = authService;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            log.warn("LIGA_ADMIN_EMAIL está vacía: no se crea ningún administrador inicial.");
            return;
        }
        if (email.strip().length() > 254 || !FORMA_DE_CORREO.matcher(email.strip()).matches()) {
            throw new IllegalStateException(
                    "LIGA_ADMIN_EMAIL no parece un correo electrónico válido. Corríjalo o déjelo vacío.");
        }
        if (authService.crearAdministradorSiNoExiste(email)) {
            log.info("Administrador inicial '{}' creado. Se enlazará con su cuenta de Google en su primer ingreso.",
                    email.strip().toLowerCase(Locale.ROOT));
        }
    }
}

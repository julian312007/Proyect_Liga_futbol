package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.UsuarioMapper;
import co.edu.uniajc.liga.repository.UsuarioRepository;
import co.edu.uniajc.liga.security.GoogleTokenVerifier;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import co.edu.uniajc.liga.security.JwtService;
import co.edu.uniajc.liga.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Implementación de {@link AuthService}. Aquí viven las reglas de cuentas: rol {@code USER} en el primer
 * ingreso, correo verificado obligatorio, reconocimiento del usuario por {@code sub} (y por correo solo
 * para enlazar), usuarios desactivados y mensaje genérico ante cualquier token inválido.
 *
 * <p>No conoce HTTP: lanza excepciones de Spring Security y de dominio, que
 * {@code GlobalExceptionHandler} traduce a 401, 403 y 404.
 *
 * <h2>Riesgo conocido: enlace por correo</h2>
 * El administrador inicial se crea solo con su correo, sin {@code google_sub}. Quien ingrese primero con
 * Google presentando ese correo <em>verificado</em> se queda con esa fila (y con el rol ADMIN). Es
 * seguro mientras Google garantice que solo el dueño del buzón obtiene una identidad con ese correo
 * verificado, pero tiene límites: si el correo es de un dominio que cambia de dueño, o de un buzón
 * compartido o reasignado, otra persona podría reclamarlo antes que el administrador legítimo. Mitigaciones
 * aplicadas: solo se enlaza una fila sin {@code sub} (nunca se reasigna una ya enlazada), se exige
 * {@code email_verified} y un usuario desactivado no se enlaza. Recomendación: usar para el administrador
 * una cuenta propia del equipo y hacer su primer ingreso apenas se despliega.
 */
@Slf4j
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    static final String NOMBRE_ADMINISTRADOR = "Administrador";

    private static final int LONGITUD_MAXIMA_NOMBRE = 100;
    private static final int LONGITUD_MAXIMA_PICTURE_URL = 500;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;

    public AuthServiceImpl(UsuarioRepository usuarioRepository,
                           UsuarioMapper usuarioMapper,
                           GoogleTokenVerifier googleTokenVerifier,
                           JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtService = jwtService;
    }

    // ------------------------------------------------------ Ingreso con Google

    /**
     * Sin transacción a propósito: verificar el token puede implicar una llamada de red a Google (al
     * descargar sus claves públicas), y no debe mantener abierta una conexión de la base de datos mientras
     * tanto. Cada operación del repositorio corre en su propia transacción y se guarda explícitamente.
     * Consecuencia: dos primeros ingresos simultáneos de la misma cuenta pueden chocar con el índice único;
     * uno recibe 409 {@code CONFLICTO_INTEGRIDAD} y basta con reintentar.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthResponse iniciarSesionConGoogle(GoogleLoginRequest request) {
        // Lanza BadCredentialsException (401) si el token es falso, vencido, de otra aplicación...
        IdentidadGoogle identidad = googleTokenVerifier.verificar(request.idToken());

        if (!identidad.emailVerificado()) {
            // Sin correo verificado no se puede confiar en que la persona sea dueña de ese correo.
            log.warn("Ingreso rechazado: Google informa que el correo de la cuenta no está verificado");
            throw credencialesInvalidas();
        }

        Optional<Usuario> existente = buscarExistente(identidad);
        if (existente.isPresent() && !existente.get().isActivo()) {
            // 403 y no 401: la identidad SÍ quedó probada por Google (autenticado), pero la cuenta no
            // tiene permiso de acceso. No revela nada que el dueño de esa cuenta de Google no sepa.
            throw new AccessDeniedException("La cuenta está desactivada");
        }

        Usuario usuario = existente.map(u -> actualizar(u, identidad)).orElseGet(() -> nuevoUsuario(identidad));
        usuario.setUltimoAcceso(Instant.now());
        Usuario guardado = usuarioRepository.save(usuario);

        String token = jwtService.generarToken(guardado.getEmail(), guardado.getRol());
        return new AuthResponse(token, AuthResponse.TIPO_BEARER, jwtService.getVigenciaSegundos(),
                guardado.getEmail(), guardado.getNombre(), guardado.getRol());
    }

    /**
     * Primero por {@code sub}. Si no hay, por correo, pero solo se acepta una fila que todavía no tenga
     * {@code sub}: una fila ya enlazada a otra cuenta de Google no se reasigna.
     */
    private Optional<Usuario> buscarExistente(IdentidadGoogle identidad) {
        Optional<Usuario> porSub = usuarioRepository.findByGoogleSub(identidad.sub());
        if (porSub.isPresent()) {
            return porSub;
        }
        Optional<Usuario> porCorreo = usuarioRepository.findByEmail(identidad.email());
        if (porCorreo.isPresent() && porCorreo.get().getGoogleSub() != null) {
            // Mismo correo, otra cuenta de Google (por ejemplo, un buzón reasignado). Nunca se enlaza.
            log.warn("Ingreso rechazado: el correo {} ya está enlazado a otra cuenta de Google", identidad.email());
            throw credencialesInvalidas();
        }
        return porCorreo;
    }

    /** Enlaza el {@code sub} (si faltaba) y refresca el correo y la foto con lo que informa Google. */
    private Usuario actualizar(Usuario usuario, IdentidadGoogle identidad) {
        usuario.setGoogleSub(identidad.sub());
        if (!identidad.email().equals(usuario.getEmail())
                && !usuarioRepository.existsByEmailAndIdNot(identidad.email(), usuario.getId())) {
            // El correo cambió en Google. Si ya lo usa otra fila se conserva el anterior en vez de fallar.
            usuario.setEmail(identidad.email());
        }
        usuario.setPictureUrl(recortarUrl(identidad.pictureUrl()));
        return usuario;
    }

    private Usuario nuevoUsuario(IdentidadGoogle identidad) {
        Usuario usuario = usuarioMapper.toEntity(identidad);
        usuario.setNombre(nombreParaMostrar(identidad));
        usuario.setPictureUrl(recortarUrl(identidad.pictureUrl()));
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    // -------------------------------------------------------------- Perfil

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPerfil(String email) {
        return usuarioMapper.toResponse(buscarPorCorreo(email));
    }

    @Override
    public UsuarioResponse actualizarPerfil(String email, ActualizarPerfilRequest request) {
        Usuario usuario = buscarPorCorreo(email);
        // La entidad está gestionada: al terminar la transacción JPA guarda el cambio de nombre.
        usuarioMapper.updateEntity(request, usuario);
        return usuarioMapper.toResponse(usuario);
    }

    // ------------------------------------------------- Administrador inicial

    @Override
    public boolean crearAdministradorSiNoExiste(String email) {
        String correo = email.strip().toLowerCase(Locale.ROOT);
        Optional<Usuario> existente = usuarioRepository.findByEmail(correo);
        if (existente.isPresent()) {
            if (existente.get().getRol() == Rol.ADMIN) {
                log.info("El administrador inicial '{}' ya existe: no se crea de nuevo.", correo);
            } else {
                // No se asciende a nadie automáticamente: sería una escalada de privilegios silenciosa.
                log.warn("LIGA_ADMIN_EMAIL '{}' corresponde a un usuario existente con rol {}: no se modifica. "
                        + "Para crear el administrador, use un correo que no haya ingresado antes.",
                        correo, existente.get().getRol());
            }
            return false;
        }
        Usuario administrador = new Usuario();
        administrador.setEmail(correo);
        administrador.setNombre(NOMBRE_ADMINISTRADOR);
        administrador.setRol(Rol.ADMIN);
        administrador.setActivo(true);
        // google_sub queda nulo: se enlaza en el primer ingreso con Google.
        usuarioRepository.save(administrador);
        return true;
    }

    // --------------------------------------------------------------- Apoyo

    private Usuario buscarPorCorreo(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", email));
    }

    private static BadCredentialsException credencialesInvalidas() {
        return new BadCredentialsException(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS);
    }

    /** El nombre de Google; si falta, la parte local del correo; en todo caso, de hasta 100 caracteres. */
    private static String nombreParaMostrar(IdentidadGoogle identidad) {
        String nombre = identidad.nombre();
        if (nombre == null || nombre.isBlank()) {
            String correo = identidad.email();
            nombre = correo.substring(0, correo.indexOf('@') > 0 ? correo.indexOf('@') : correo.length());
        }
        return nombre.length() > LONGITUD_MAXIMA_NOMBRE ? nombre.substring(0, LONGITUD_MAXIMA_NOMBRE) : nombre;
    }

    /** La foto es opcional: si la URL no cabe en la columna, se descarta en vez de truncarla (quedaría rota). */
    private static String recortarUrl(String url) {
        return url == null || url.isBlank() || url.length() > LONGITUD_MAXIMA_PICTURE_URL ? null : url;
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.UsuarioMapperImpl;
import co.edu.uniajc.liga.repository.UsuarioRepository;
import co.edu.uniajc.liga.security.GoogleTokenVerifier;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import co.edu.uniajc.liga.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link AuthServiceImpl} con Mockito: sin Spring, sin red y sin Docker. El mapper
 * es la implementación real generada por MapStruct; el verificador de Google, el JWT y el repositorio se
 * simulan. Cubre las reglas de cuentas del ingreso con Google (D-1): usuario nuevo = USER, usuario
 * existente, administrador inicial, enlace por correo, correo sin verificar y usuario inactivo.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String ID_TOKEN = "id-token-ficticio-de-google";
    private static final GoogleLoginRequest SOLICITUD = new GoogleLoginRequest(ID_TOKEN);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private JwtService jwtService;

    private AuthServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new AuthServiceImpl(usuarioRepository, new UsuarioMapperImpl(), googleTokenVerifier, jwtService);
    }

    // ============================================== Ingreso: usuario nuevo

    @Test
    @DisplayName("Primer ingreso de una cuenta nueva: se crea con rol USER y se devuelve el JWT propio")
    void usuarioNuevo_seCreaConRolUser() {
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "ana@example.com", true, "Ana Pérez"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario guardado = invocacion.getArgument(0);
            guardado.setId(5L);
            return guardado;
        });
        when(jwtService.generarToken("ana@example.com", Rol.USER)).thenReturn("token-propio");
        when(jwtService.getVigenciaSegundos()).thenReturn(3600L);

        AuthResponse respuesta = servicio.iniciarSesionConGoogle(SOLICITUD);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario creado = captor.getValue();
        assertThat(creado.getGoogleSub()).isEqualTo("g-1");
        assertThat(creado.getEmail()).isEqualTo("ana@example.com");
        assertThat(creado.getNombre()).isEqualTo("Ana Pérez");
        assertThat(creado.getPictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/foto");
        assertThat(creado.getRol()).isEqualTo(Rol.USER);
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getUltimoAcceso()).isNotNull();

        assertThat(respuesta.token()).isEqualTo("token-propio");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEn()).isEqualTo(3600L);
        assertThat(respuesta.email()).isEqualTo("ana@example.com");
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
        assertThat(respuesta.rol()).isEqualTo(Rol.USER);
    }

    @Test
    @DisplayName("Cuenta nueva sin nombre en Google: usa la parte local del correo")
    void usuarioNuevo_sinNombre_usaLaParteLocalDelCorreo() {
        prepararIngresoDeUsuarioNuevo(identidad("g-1", "ana.perez@example.com", true, null));

        servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getNombre()).isEqualTo("ana.perez");
    }

    @Test
    @DisplayName("Cuenta nueva con un nombre de más de 100 caracteres: se recorta a 100")
    void usuarioNuevo_nombreMuyLargo_seRecorta() {
        prepararIngresoDeUsuarioNuevo(identidad("g-1", "ana@example.com", true, "N".repeat(150)));

        servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getNombre()).hasSize(100);
    }

    @Test
    @DisplayName("Cuenta nueva con una URL de foto de más de 500 caracteres: la foto se descarta")
    void usuarioNuevo_fotoDemasiadoLarga_seDescarta() {
        IdentidadGoogle conFotoLarga = new IdentidadGoogle("g-1", "ana@example.com", true, "Ana",
                "https://lh3.googleusercontent.com/" + "x".repeat(500));
        prepararIngresoDeUsuarioNuevo(conFotoLarga);

        servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getPictureUrl()).isNull();
    }

    // ====================================== Ingreso: usuario existente por sub

    @Test
    @DisplayName("Usuario existente (por sub): conserva su rol y su nombre editado, y se refrescan foto y último acceso")
    void usuarioExistente_conservaRolYNombre() {
        Usuario existente = usuario(7L, "admin@example.com", "g-1", Rol.ADMIN, true);
        existente.setNombre("Nombre que editó el usuario");
        when(googleTokenVerifier.verificar(ID_TOKEN))
                .thenReturn(identidad("g-1", "admin@example.com", true, "Nombre de Google"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(jwtService.generarToken("admin@example.com", Rol.ADMIN)).thenReturn("token-admin");
        when(jwtService.getVigenciaSegundos()).thenReturn(3600L);

        AuthResponse respuesta = servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
        assertThat(respuesta.nombre()).isEqualTo("Nombre que editó el usuario");
        assertThat(guardado().getPictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/foto");
        assertThat(guardado().getUltimoAcceso()).isNotNull();
        // Se encontró por sub: no hace falta (ni conviene) buscar por correo.
        verify(usuarioRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Si el correo cambió en Google y nadie más lo usa: se actualiza, y el JWT lleva el correo nuevo")
    void correoCambiaEnGoogle_seActualiza() {
        Usuario existente = usuario(7L, "viejo@example.com", "g-1", Rol.USER, true);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "nuevo@example.com", true, "Ana"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmailAndIdNot("nuevo@example.com", 7L)).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(jwtService.generarToken("nuevo@example.com", Rol.USER)).thenReturn("token");

        AuthResponse respuesta = servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getEmail()).isEqualTo("nuevo@example.com");
        assertThat(respuesta.email()).isEqualTo("nuevo@example.com");
    }

    @Test
    @DisplayName("Si el correo cambió en Google pero ya lo usa otro usuario: se conserva el anterior y no falla")
    void correoCambiaEnGoogle_peroLoUsaOtro_seConserva() {
        Usuario existente = usuario(7L, "viejo@example.com", "g-1", Rol.USER, true);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "ocupado@example.com", true, "Ana"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByEmailAndIdNot("ocupado@example.com", 7L)).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(jwtService.generarToken("viejo@example.com", Rol.USER)).thenReturn("token");

        servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getEmail()).isEqualTo("viejo@example.com");
    }

    // ============================================== Ingreso: enlace por correo

    @Test
    @DisplayName("Administrador creado solo con su correo: en su primer ingreso se enlaza el sub y conserva el rol ADMIN")
    void administradorPrecreado_seEnlazaPorCorreoYConservaElRol() {
        Usuario admin = usuario(1L, "admin@example.com", null, Rol.ADMIN, true);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-9", "admin@example.com", true, "Admin"));
        when(usuarioRepository.findByGoogleSub("g-9")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(jwtService.generarToken("admin@example.com", Rol.ADMIN)).thenReturn("token-admin");

        AuthResponse respuesta = servicio.iniciarSesionConGoogle(SOLICITUD);

        assertThat(guardado().getGoogleSub()).isEqualTo("g-9");
        assertThat(guardado().getId()).isEqualTo(1L);
        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    @DisplayName("Correo ya enlazado a OTRA cuenta de Google: 401 genérico y no se enlaza ni se guarda nada")
    void correoEnlazadoAOtraCuenta_seRechaza() {
        Usuario deOtraCuenta = usuario(3L, "ana@example.com", "g-ORIGINAL", Rol.ADMIN, true);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-IMPOSTOR", "ana@example.com", true, "Ana"));
        when(usuarioRepository.findByGoogleSub("g-IMPOSTOR")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(deOtraCuenta));

        assertThatThrownBy(() -> servicio.iniciarSesionConGoogle(SOLICITUD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS);

        assertThat(deOtraCuenta.getGoogleSub()).isEqualTo("g-ORIGINAL");
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    // ======================================================= Ingreso: rechazos

    @Test
    @DisplayName("Correo sin verificar en Google: 401 genérico, sin tocar la base de datos ni emitir token")
    void emailNoVerificado_seRechaza() {
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "ana@example.com", false, "Ana"));

        assertThatThrownBy(() -> servicio.iniciarSesionConGoogle(SOLICITUD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS);

        verifyNoInteractions(usuarioRepository, jwtService);
    }

    @Test
    @DisplayName("Token de Google inválido: se propaga el 401 del verificador, sin tocar la base de datos")
    void tokenInvalido_seRechazaSinTocarLaBaseDeDatos() {
        when(googleTokenVerifier.verificar(ID_TOKEN))
                .thenThrow(new BadCredentialsException(GoogleTokenVerifier.MENSAJE_CREDENCIALES_INVALIDAS));

        assertThatThrownBy(() -> servicio.iniciarSesionConGoogle(SOLICITUD))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(usuarioRepository, jwtService);
    }

    @Test
    @DisplayName("Usuario desactivado (por sub): 403, sin guardar nada ni emitir token")
    void usuarioInactivo_seRechazaConAccesoDenegado() {
        Usuario inactivo = usuario(7L, "ana@example.com", "g-1", Rol.USER, false);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "ana@example.com", true, "Ana"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.iniciarSesionConGoogle(SOLICITUD))
                .isInstanceOf(AccessDeniedException.class);

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Usuario desactivado encontrado por correo: 403 y NO se le enlaza el sub")
    void usuarioInactivoPorCorreo_noSeEnlaza() {
        Usuario inactivo = usuario(7L, "ana@example.com", null, Rol.ADMIN, false);
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad("g-1", "ana@example.com", true, "Ana"));
        when(usuarioRepository.findByGoogleSub("g-1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.iniciarSesionConGoogle(SOLICITUD))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(inactivo.getGoogleSub()).isNull();
        verify(usuarioRepository, never()).save(any());
    }

    // ================================================================= Perfil

    @Test
    @DisplayName("Ver el perfil de un usuario existente")
    void obtenerPerfil_usuarioExistente_devuelveElPerfil() {
        when(usuarioRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(usuario(7L, "ana@example.com", "g-1", Rol.USER, true)));

        UsuarioResponse perfil = servicio.obtenerPerfil("ana@example.com");

        assertThat(perfil.id()).isEqualTo(7L);
        assertThat(perfil.email()).isEqualTo("ana@example.com");
        assertThat(perfil.rol()).isEqualTo(Rol.USER);
        // La respuesta no tiene ningún campo con el identificador interno de Google.
        assertThat(perfil.toString()).doesNotContain("g-1");
    }

    @Test
    @DisplayName("Ver el perfil de un usuario inexistente: 404")
    void obtenerPerfil_usuarioInexistente_lanzaNoEncontrado() {
        when(usuarioRepository.findByEmail("fantasma@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPerfil("fantasma@example.com"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Editar el perfil cambia solo el nombre; el correo y el rol no se tocan")
    void actualizarPerfil_cambiaSoloElNombre() {
        Usuario usuario = usuario(7L, "ana@example.com", "g-1", Rol.USER, true);
        when(usuarioRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario));

        UsuarioResponse perfil = servicio.actualizarPerfil("ana@example.com", new ActualizarPerfilRequest("  Ana María  "));

        assertThat(perfil.nombre()).isEqualTo("Ana María");
        assertThat(usuario.getEmail()).isEqualTo("ana@example.com");
        assertThat(usuario.getRol()).isEqualTo(Rol.USER);
        assertThat(usuario.getGoogleSub()).isEqualTo("g-1");
    }

    @Test
    @DisplayName("Editar el perfil de un usuario inexistente: 404")
    void actualizarPerfil_usuarioInexistente_lanzaNoEncontrado() {
        when(usuarioRepository.findByEmail("fantasma@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarPerfil("fantasma@example.com", new ActualizarPerfilRequest("X")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // =========================================================== Administrador inicial

    @Test
    @DisplayName("Administrador inicial inexistente: se crea con rol ADMIN, correo normalizado y sin google_sub")
    void crearAdministrador_noExiste_loCreaSinSub() {
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        boolean creado = servicio.crearAdministradorSiNoExiste("  Admin@Example.COM ");

        assertThat(creado).isTrue();
        Usuario admin = guardado();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getGoogleSub()).isNull();
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.getNombre()).isEqualTo(AuthServiceImpl.NOMBRE_ADMINISTRADOR);
    }

    @Test
    @DisplayName("Administrador inicial ya existente: es idempotente, no crea ni modifica nada")
    void crearAdministrador_yaExiste_noHaceNada() {
        when(usuarioRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(usuario(1L, "admin@example.com", "g-9", Rol.ADMIN, true)));

        boolean creado = servicio.crearAdministradorSiNoExiste("admin@example.com");

        assertThat(creado).isFalse();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el correo del administrador es de un usuario normal ya registrado: no se le sube el rol")
    void crearAdministrador_correoDeUnUsuarioNormal_noLoAsciende() {
        Usuario normal = usuario(2L, "admin@example.com", "g-2", Rol.USER, true);
        when(usuarioRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(normal));

        boolean creado = servicio.crearAdministradorSiNoExiste("admin@example.com");

        assertThat(creado).isFalse();
        assertThat(normal.getRol()).isEqualTo(Rol.USER);
        verify(usuarioRepository, never()).save(any());
    }

    // ===================================================================== Apoyo

    private void prepararIngresoDeUsuarioNuevo(IdentidadGoogle identidad) {
        when(googleTokenVerifier.verificar(ID_TOKEN)).thenReturn(identidad);
        when(usuarioRepository.findByGoogleSub(identidad.sub())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail(identidad.email())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(jwtService.generarToken(any(), any())).thenReturn("token");
    }

    /** El usuario que se pasó a {@code save} (la última vez). */
    private Usuario guardado() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    private static IdentidadGoogle identidad(String sub, String email, boolean verificado, String nombre) {
        return new IdentidadGoogle(sub, email, verificado, nombre, "https://lh3.googleusercontent.com/a/foto");
    }

    private static Usuario usuario(Long id, String email, String googleSub, Rol rol, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setGoogleSub(googleSub);
        usuario.setNombre("Nombre " + email);
        usuario.setRol(rol);
        usuario.setActivo(activo);
        usuario.setFechaCreacion(Instant.parse("2026-11-02T10:00:00Z"));
        return usuario;
    }
}

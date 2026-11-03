package co.edu.uniajc.liga.dto;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.request.GoogleLoginRequest;
import co.edu.uniajc.liga.dto.response.AuthResponse;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.entity.Rol;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba unitaria de los objetos de datos de la autenticación con Google: normalización, validación y
 * que ninguno pueda filtrar una credencial por {@code toString}. Sin Spring ni Docker.
 */
class DatosDeAutenticacionTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    @Test
    @DisplayName("IdentidadGoogle normaliza el correo (sin espacios y en minúsculas) y recorta nombre y foto")
    void identidadGoogle_normalizaElCorreo() {
        IdentidadGoogle identidad = new IdentidadGoogle("sub-1", "  Ana.Perez@Example.COM ", true,
                "  Ana Pérez ", " https://lh3.googleusercontent.com/a/foto ");

        assertThat(identidad.email()).isEqualTo("ana.perez@example.com");
        assertThat(identidad.nombre()).isEqualTo("Ana Pérez");
        assertThat(identidad.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/foto");
        assertThat(identidad.sub()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("IdentidadGoogle acepta nombre y foto nulos")
    void identidadGoogle_aceptaNulos() {
        IdentidadGoogle identidad = new IdentidadGoogle("sub-1", "ana@example.com", false, null, null);

        assertThat(identidad.nombre()).isNull();
        assertThat(identidad.pictureUrl()).isNull();
        assertThat(identidad.emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("GoogleLoginRequest: un idToken normal es válido; vacío, en blanco, nulo o gigante no")
    void googleLoginRequest_validaElIdToken() {
        assertThat(validador.validate(new GoogleLoginRequest("eyJhbGciOi.payload.firma"))).isEmpty();
        assertThat(validador.validate(new GoogleLoginRequest(""))).isNotEmpty();
        assertThat(validador.validate(new GoogleLoginRequest("   "))).isNotEmpty();
        assertThat(validador.validate(new GoogleLoginRequest(null))).isNotEmpty();
        assertThat(validador.validate(new GoogleLoginRequest("x".repeat(8193)))).isNotEmpty();
        assertThat(validador.validate(new GoogleLoginRequest("x".repeat(8192)))).isEmpty();
    }

    @Test
    @DisplayName("GoogleLoginRequest solo tiene el campo idToken: el cliente no puede enviar rol ni datos del usuario")
    void googleLoginRequest_soloTieneElIdToken() {
        assertThat(Arrays.stream(GoogleLoginRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("idToken");
    }

    @Test
    @DisplayName("ActualizarPerfilRequest: recorta el nombre; en blanco o de más de 100 caracteres no es válido")
    void actualizarPerfilRequest_validaElNombre() {
        assertThat(new ActualizarPerfilRequest("  Ana María ").nombre()).isEqualTo("Ana María");
        assertThat(validador.validate(new ActualizarPerfilRequest("Ana"))).isEmpty();
        assertThat(validador.validate(new ActualizarPerfilRequest("   "))).isNotEmpty();
        assertThat(validador.validate(new ActualizarPerfilRequest(null))).isNotEmpty();
        assertThat(validador.validate(new ActualizarPerfilRequest("N".repeat(101)))).isNotEmpty();
    }

    @Test
    @DisplayName("ActualizarPerfilRequest solo tiene el campo nombre: no se puede cambiar el correo ni el rol")
    void actualizarPerfilRequest_soloTieneElNombre() {
        assertThat(Arrays.stream(ActualizarPerfilRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("nombre");
    }

    @Test
    @DisplayName("toString de GoogleLoginRequest y AuthResponse no imprime el ID token ni el JWT")
    void toString_noExponeCredenciales() {
        GoogleLoginRequest solicitud = new GoogleLoginRequest("id-token-secreto-de-google");
        AuthResponse respuesta = new AuthResponse("jwt-secreto-propio", "Bearer", 3600, "ana@example.com", "Ana", Rol.USER);

        assertThat(solicitud.toString()).doesNotContain("id-token-secreto-de-google");
        assertThat(respuesta.toString()).doesNotContain("jwt-secreto-propio").contains("ana@example.com");
    }

    @Test
    @DisplayName("UsuarioResponse no tiene ningún campo con el identificador interno de Google")
    void usuarioResponse_noTieneGoogleSub() {
        assertThat(Arrays.stream(UsuarioResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("googleSub", "sub", "passwordHash", "password");
    }
}

package co.edu.uniajc.liga.config;

import co.edu.uniajc.liga.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link AdminInicializador}: sin Spring ni Docker. El servicio se simula. Los
 * correos son ficticios (example.com es un dominio reservado).
 */
@ExtendWith(MockitoExtension.class)
class AdminInicializadorTest {

    @Mock
    private AuthService authService;

    @Test
    @DisplayName("Con un correo válido: pide crear el ADMIN con ese correo")
    void correoValido_creaElAdministrador() {
        when(authService.crearAdministradorSiNoExiste("admin@example.com")).thenReturn(true);

        ejecutar("admin@example.com");

        verify(authService).crearAdministradorSiNoExiste("admin@example.com");
    }

    @Test
    @DisplayName("Si el administrador ya existe: no falla (es idempotente, el servicio devuelve false)")
    void administradorYaExistente_noFalla() {
        when(authService.crearAdministradorSiNoExiste("admin@example.com")).thenReturn(false);

        assertThatCode(() -> ejecutar("admin@example.com")).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "LIGA_ADMIN_EMAIL \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Con la variable vacía o en blanco: no crea nada y no falla")
    void sinConfiguracion_noCreaNada(String vacio) {
        assertThatCode(() -> ejecutar(vacio)).doesNotThrowAnyException();

        verifyNoInteractions(authService);
    }

    @ParameterizedTest(name = "LIGA_ADMIN_EMAIL \"{0}\"")
    @ValueSource(strings = {"no-es-un-correo", "sin-dominio@", "@sin-usuario.com", "dos@@example.com",
            "con espacio@example.com", "sin-punto@example"})
    @DisplayName("Con algo que no parece un correo: la aplicación no arranca y no se crea nada")
    void correoInvalido_falla(String invalido) {
        assertThatThrownBy(() -> ejecutar(invalido))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIGA_ADMIN_EMAIL");

        verifyNoInteractions(authService);
    }

    private void ejecutar(String email) {
        new AdminInicializador(authService, email).run(new DefaultApplicationArguments());
    }
}

package co.edu.uniajc.liga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para editar el perfil propio ({@code PUT /api/v1/auth/me}). Opcional del requisito D-1.
 *
 * <p>Solo se puede cambiar el nombre para mostrar: el correo y la foto los entrega Google y se
 * actualizan en cada ingreso, y el rol solo lo cambia un administrador.
 *
 * @param nombre nuevo nombre para mostrar (se recortan los espacios de los extremos)
 */
public record ActualizarPerfilRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre) {

    public ActualizarPerfilRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}

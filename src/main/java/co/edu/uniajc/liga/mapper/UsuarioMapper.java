package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.ActualizarPerfilRequest;
import co.edu.uniajc.liga.dto.response.UsuarioResponse;
import co.edu.uniajc.liga.entity.Usuario;
import co.edu.uniajc.liga.security.IdentidadGoogle;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Usuario}, sus DTO y la identidad verificada de Google. MapStruct genera la
 * implementación como bean de Spring (por el argumento {@code -Amapstruct.defaultComponentModel=spring}
 * del {@code pom.xml}).
 *
 * <p>No accede a repositorios: el rol, el estado y las fechas los fija {@code AuthServiceImpl}, que es
 * quien aplica las reglas.
 */
@Mapper
public interface UsuarioMapper {

    /** No copia el {@code google_sub}: {@link UsuarioResponse} no tiene ese campo. */
    UsuarioResponse toResponse(Usuario usuario);

    /**
     * Copia el {@code sub} (como {@code googleSub}), el correo, el nombre y la foto de la identidad de
     * Google. El id, el rol, el estado y las fechas se ignoran a propósito: los asigna el servicio (el
     * rol es siempre decisión del servidor, nunca del token ni del cliente).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "googleSub", source = "sub")
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "ultimoAcceso", ignore = true)
    Usuario toEntity(IdentidadGoogle identidad);

    /** Actualiza solo el nombre. El resto de campos de la entidad no se tocan. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nombre", source = "nombre")
    void updateEntity(ActualizarPerfilRequest request, @MappingTarget Usuario usuario);
}

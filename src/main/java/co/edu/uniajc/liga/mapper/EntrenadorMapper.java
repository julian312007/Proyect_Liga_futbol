package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Entrenador} y sus DTO. MapStruct genera la implementación como bean de Spring.
 * No accede a repositorios ni normaliza texto: eso lo hace {@code EntrenadorServiceImpl}.
 */
@Mapper
public interface EntrenadorMapper {

    /** El id lo asigna la base de datos: nunca se copia desde el cliente. */
    @Mapping(target = "id", ignore = true)
    Entrenador toEntity(EntrenadorRequest request);

    EntrenadorResponse toResponse(Entrenador entrenador);

    /** Reemplaza los datos del entrenador; conserva su id. */
    @Mapping(target = "id", ignore = true)
    void updateEntity(EntrenadorRequest request, @MappingTarget Entrenador entrenador);
}

package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.entity.Estadio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Estadio} y sus DTO. MapStruct genera la implementación como bean de Spring.
 * No accede a repositorios ni normaliza texto: eso lo hace {@code EstadioServiceImpl} (EST-01).
 */
@Mapper
public interface EstadioMapper {

    /** El id lo asigna la base de datos: nunca se copia desde el cliente. */
    @Mapping(target = "id", ignore = true)
    Estadio toEntity(EstadioRequest request);

    EstadioResponse toResponse(Estadio estadio);

    /** Reemplaza los datos del estadio; conserva su id. */
    @Mapping(target = "id", ignore = true)
    void updateEntity(EstadioRequest request, @MappingTarget Estadio estadio);
}

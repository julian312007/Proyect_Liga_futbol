package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Arbitro} y sus DTO. MapStruct genera la implementación como bean de Spring.
 * No accede a repositorios ni normaliza texto: eso lo hace {@code ArbitroServiceImpl}.
 */
@Mapper
public interface ArbitroMapper {

    /** El id lo asigna la base de datos: nunca se copia desde el cliente. */
    @Mapping(target = "id", ignore = true)
    Arbitro toEntity(ArbitroRequest request);

    ArbitroResponse toResponse(Arbitro arbitro);

    /** Reemplaza los datos del árbitro; conserva su id. */
    @Mapping(target = "id", ignore = true)
    void updateEntity(ArbitroRequest request, @MappingTarget Arbitro arbitro);
}

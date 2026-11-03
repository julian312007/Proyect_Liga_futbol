package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.entity.Liga;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Liga} y sus DTO. MapStruct genera la implementación como bean de Spring.
 * No accede a repositorios ni valida fechas: eso lo hace {@code LigaServiceImpl}.
 */
@Mapper
public interface LigaMapper {

    /** El id lo asigna la base de datos: nunca se copia desde el cliente. */
    @Mapping(target = "id", ignore = true)
    Liga toEntity(LigaRequest request);

    LigaResponse toResponse(Liga liga);

    /** Reemplaza los datos de la liga; conserva su id. */
    @Mapping(target = "id", ignore = true)
    void updateEntity(LigaRequest request, @MappingTarget Liga liga);
}

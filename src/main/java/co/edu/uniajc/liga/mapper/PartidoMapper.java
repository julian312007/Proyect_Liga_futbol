package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Partido} y sus DTO. MapStruct genera la implementación como bean de Spring.
 *
 * <p>No accede a repositorios: las relaciones (liga, equipos, estadio, árbitro) las resuelve por id
 * {@code PartidoServiceImpl}, que es quien aplica las reglas. Tampoco copia el marcador ni el estado desde la
 * solicitud: los gobierna el ciclo de vida del partido, nunca el cliente.
 */
@Mapper
public interface PartidoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "liga", ignore = true)
    @Mapping(target = "equipoLocal", ignore = true)
    @Mapping(target = "equipoVisitante", ignore = true)
    @Mapping(target = "estadio", ignore = true)
    @Mapping(target = "arbitro", ignore = true)
    @Mapping(target = "golesLocal", ignore = true)
    @Mapping(target = "golesVisitante", ignore = true)
    @Mapping(target = "estado", ignore = true)
    Partido toEntity(PartidoRequest request);

    PartidoResponse toResponse(Partido partido);

    /** Actualiza solo la jornada y la fecha y hora; las relaciones las reasigna el servicio. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "liga", ignore = true)
    @Mapping(target = "equipoLocal", ignore = true)
    @Mapping(target = "equipoVisitante", ignore = true)
    @Mapping(target = "estadio", ignore = true)
    @Mapping(target = "arbitro", ignore = true)
    @Mapping(target = "golesLocal", ignore = true)
    @Mapping(target = "golesVisitante", ignore = true)
    @Mapping(target = "estado", ignore = true)
    void updateEntity(PartidoRequest request, @MappingTarget Partido partido);

    default ResumenResponse resumen(Liga liga) {
        return liga == null ? null : new ResumenResponse(liga.getId(), liga.getNombre());
    }

    default ResumenResponse resumen(Equipo equipo) {
        return equipo == null ? null : new ResumenResponse(equipo.getId(), equipo.getNombre());
    }

    default ResumenResponse resumen(Estadio estadio) {
        return estadio == null ? null : new ResumenResponse(estadio.getId(), estadio.getNombre());
    }

    default ResumenResponse resumen(Arbitro arbitro) {
        return arbitro == null ? null : new ResumenResponse(arbitro.getId(), arbitro.getNombre());
    }
}

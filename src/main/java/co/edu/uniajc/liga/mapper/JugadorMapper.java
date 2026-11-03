package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Jugador} y sus DTO. MapStruct genera un bean de Spring. No accede a repositorios:
 * el equipo lo resuelve por id {@code JugadorServiceImpl}, por eso se ignora al convertir desde el
 * {@link JugadorRequest}.
 */
@Mapper
public interface JugadorMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "equipo", ignore = true)
    Jugador toEntity(JugadorRequest request);

    JugadorResponse toResponse(Jugador jugador);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "equipo", ignore = true)
    void updateEntity(JugadorRequest request, @MappingTarget Jugador jugador);

    default ResumenResponse resumen(Equipo equipo) {
        return equipo == null ? null : new ResumenResponse(equipo.getId(), equipo.getNombre());
    }
}

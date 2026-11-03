package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Tarjeta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversión entre {@link Tarjeta} y sus DTO. No accede a repositorios: el partido y el jugador los
 * resuelve {@code TarjetaServiceImpl} por id.
 */
@Mapper
public interface TarjetaMapper {

    @Mapping(target = "partidoId", source = "partido.id")
    @Mapping(target = "jugador", source = "jugador")
    @Mapping(target = "equipo", source = "jugador.equipo")
    TarjetaResponse toResponse(Tarjeta tarjeta);

    /** Ignora el id y las relaciones (partido y jugador): las asigna el servicio. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "partido", ignore = true)
    @Mapping(target = "jugador", ignore = true)
    Tarjeta toEntity(TarjetaRequest request);

    default ResumenResponse resumen(Jugador jugador) {
        return jugador == null ? null : new ResumenResponse(jugador.getId(), jugador.getNombre());
    }

    default ResumenResponse resumen(Equipo equipo) {
        return equipo == null ? null : new ResumenResponse(equipo.getId(), equipo.getNombre());
    }
}

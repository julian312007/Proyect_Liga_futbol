package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversión entre {@link Gol} y sus DTO. No accede a repositorios: el partido y el jugador los resuelve
 * {@code GolServiceImpl} por id.
 */
@Mapper
public interface GolMapper {

    @Mapping(target = "partidoId", source = "partido.id")
    @Mapping(target = "jugador", source = "jugador")
    @Mapping(target = "equipo", source = "jugador.equipo")
    GolResponse toResponse(Gol gol);

    /** Ignora el id y las relaciones (partido y jugador): las asigna el servicio. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "partido", ignore = true)
    @Mapping(target = "jugador", ignore = true)
    Gol toEntity(GolRequest request);

    default ResumenResponse resumen(Jugador jugador) {
        return jugador == null ? null : new ResumenResponse(jugador.getId(), jugador.getNombre());
    }

    default ResumenResponse resumen(Equipo equipo) {
        return equipo == null ? null : new ResumenResponse(equipo.getId(), equipo.getNombre());
    }
}

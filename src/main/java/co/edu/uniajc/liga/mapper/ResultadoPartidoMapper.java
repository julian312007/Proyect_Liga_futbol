package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.entity.Partido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversión de un {@link Partido} ya finalizado a {@link ResultadoPartidoResponse}. */
@Mapper
public interface ResultadoPartidoMapper {

    @Mapping(target = "partidoId", source = "id")
    @Mapping(target = "ligaId", source = "liga.id")
    ResultadoPartidoResponse toResponse(Partido partido);
}

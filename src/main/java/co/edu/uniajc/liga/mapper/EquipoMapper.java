package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Conversión entre {@link Equipo} y sus DTO. Como en {@code UsuarioMapper}, MapStruct genera un bean de
 * Spring. No accede a repositorios: las relaciones (liga, estadio local, entrenador) las resuelve por id
 * {@code EquipoServiceImpl}, por eso se ignoran al convertir desde el {@link EquipoRequest}.
 */
@Mapper
public interface EquipoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadioLocal", ignore = true)
    @Mapping(target = "entrenador", ignore = true)
    @Mapping(target = "liga", ignore = true)
    Equipo toEntity(EquipoRequest request);

    EquipoResponse toResponse(Equipo equipo);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadioLocal", ignore = true)
    @Mapping(target = "entrenador", ignore = true)
    @Mapping(target = "liga", ignore = true)
    void updateEntity(EquipoRequest request, @MappingTarget Equipo equipo);

    /** Cuerpo técnico del equipo: hoy, su entrenador. Los datos del entrenador se leen dentro de la transacción. */
    default CuerpoTecnicoResponse toCuerpoTecnico(Equipo equipo) {
        if (equipo == null) {
            return null;
        }
        Entrenador entrenador = equipo.getEntrenador();
        return new CuerpoTecnicoResponse(
                equipo.getId(),
                equipo.getNombre(),
                entrenador == null ? null : entrenador.getId(),
                entrenador == null ? null : entrenador.getNombre(),
                entrenador == null ? null : entrenador.getNacionalidad());
    }

    default ResumenResponse resumen(Estadio estadio) {
        return estadio == null ? null : new ResumenResponse(estadio.getId(), estadio.getNombre());
    }

    default ResumenResponse resumen(Entrenador entrenador) {
        return entrenador == null ? null : new ResumenResponse(entrenador.getId(), entrenador.getNombre());
    }

    default ResumenResponse resumen(Liga liga) {
        return liga == null ? null : new ResumenResponse(liga.getId(), liga.getNombre());
    }
}

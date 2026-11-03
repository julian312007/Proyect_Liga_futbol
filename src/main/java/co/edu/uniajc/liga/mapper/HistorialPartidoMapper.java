package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse.Resultado;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import org.springframework.stereotype.Component;

/**
 * Convierte un {@link Partido} en la fila del historial de un equipo. Es una clase escrita a mano (y no una
 * interfaz de MapStruct) porque el resultado depende del equipo consultado, un dato que no está en la entidad.
 * No usa repositorios: espera la liga y los equipos ya cargados por la consulta.
 */
@Component
public class HistorialPartidoMapper {

    /**
     * @param partido  partido con liga, local y visitante ya cargados
     * @param equipoId equipo desde cuya perspectiva se calcula {@code esLocal} y {@code resultado}
     */
    public PartidoHistorialResponse toResponse(Partido partido, Long equipoId) {
        boolean esLocal = partido.getEquipoLocal() != null && equipoId.equals(partido.getEquipoLocal().getId());
        return new PartidoHistorialResponse(
                partido.getId(),
                resumen(partido.getLiga()),
                partido.getJornada(),
                partido.getFechaHora(),
                resumen(partido.getEquipoLocal()),
                resumen(partido.getEquipoVisitante()),
                partido.getGolesLocal(),
                partido.getGolesVisitante(),
                partido.getEstado(),
                esLocal,
                resultado(partido, esLocal));
    }

    /** Solo un partido finalizado tiene resultado; antes, el marcador es provisional. */
    private static Resultado resultado(Partido partido, boolean esLocal) {
        if (partido.getEstado() != EstadoPartido.FINALIZADO) {
            return null;
        }
        int favor = esLocal ? partido.getGolesLocal() : partido.getGolesVisitante();
        int contra = esLocal ? partido.getGolesVisitante() : partido.getGolesLocal();
        if (favor > contra) {
            return Resultado.VICTORIA;
        }
        return favor == contra ? Resultado.EMPATE : Resultado.DERROTA;
    }

    private static ResumenResponse resumen(Liga liga) {
        return liga == null ? null : new ResumenResponse(liga.getId(), liga.getNombre());
    }

    private static ResumenResponse resumen(Equipo equipo) {
        return equipo == null ? null : new ResumenResponse(equipo.getId(), equipo.getNombre());
    }
}

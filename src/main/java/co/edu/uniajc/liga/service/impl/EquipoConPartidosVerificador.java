package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/** Un equipo que juega o jugó partidos, como local o como visitante, no se elimina (409 {@code CONFLICTO_DEPENDENCIAS}). */
@Component
public class EquipoConPartidosVerificador implements VerificadorEliminacion {

    private final PartidoRepository partidoRepository;

    public EquipoConPartidosVerificador(PartidoRepository partidoRepository) {
        this.partidoRepository = partidoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Equipo.class;
    }

    @Override
    public void verificar(Long id) {
        if (partidoRepository.existsByEquipoLocalIdOrEquipoVisitanteId(id, id)) {
            throw new ConflictoException("CONFLICTO_DEPENDENCIAS",
                    "No se puede eliminar el equipo: juega o jugó partidos.");
        }
    }
}

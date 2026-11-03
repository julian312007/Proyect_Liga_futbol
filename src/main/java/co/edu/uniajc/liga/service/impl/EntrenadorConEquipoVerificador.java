package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/**
 * Un entrenador no se elimina mientras dirija a un equipo (409, {@code CONFLICTO_DEPENDENCIAS}). La Fase 3 lo
 * publica como bean; {@code EntrenadorService} (Fase 2) lo consulta a través de {@code VerificadoresEliminacion}.
 */
@Component
public class EntrenadorConEquipoVerificador implements VerificadorEliminacion {

    static final String CODIGO = "CONFLICTO_DEPENDENCIAS";

    private final EquipoRepository equipoRepository;

    public EntrenadorConEquipoVerificador(EquipoRepository equipoRepository) {
        this.equipoRepository = equipoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Entrenador.class;
    }

    @Override
    public void verificar(Long id) {
        if (equipoRepository.existsByEntrenadorId(id)) {
            throw new ConflictoException(CODIGO,
                    "No se puede eliminar el entrenador: está asignado a un equipo.");
        }
    }
}

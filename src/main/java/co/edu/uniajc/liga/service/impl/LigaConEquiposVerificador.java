package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/**
 * Una liga no se elimina si tiene equipos (409, {@code CONFLICTO_DEPENDENCIAS}). La Fase 3 lo publica como bean;
 * {@code LigaService} (Fase 2) lo consulta a través de {@code VerificadoresEliminacion}.
 */
@Component
public class LigaConEquiposVerificador implements VerificadorEliminacion {

    static final String CODIGO = "CONFLICTO_DEPENDENCIAS";

    private final EquipoRepository equipoRepository;

    public LigaConEquiposVerificador(EquipoRepository equipoRepository) {
        this.equipoRepository = equipoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Liga.class;
    }

    @Override
    public void verificar(Long id) {
        if (equipoRepository.existsByLigaId(id)) {
            throw new ConflictoException(CODIGO, "No se puede eliminar la liga: tiene equipos registrados.");
        }
    }
}

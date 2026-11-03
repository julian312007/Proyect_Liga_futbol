package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/** Una liga con partidos no se elimina (409 {@code CONFLICTO_DEPENDENCIAS}): se perdería el historial. */
@Component
public class LigaConPartidosVerificador implements VerificadorEliminacion {

    private final PartidoRepository partidoRepository;

    public LigaConPartidosVerificador(PartidoRepository partidoRepository) {
        this.partidoRepository = partidoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Liga.class;
    }

    @Override
    public void verificar(Long id) {
        if (partidoRepository.existsByLigaId(id)) {
            throw new ConflictoException("CONFLICTO_DEPENDENCIAS", "No se puede eliminar la liga: tiene partidos.");
        }
    }
}

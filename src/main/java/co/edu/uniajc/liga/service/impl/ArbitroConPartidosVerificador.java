package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/** Un árbitro que dirigió o dirigirá partidos no se elimina (409 {@code CONFLICTO_DEPENDENCIAS}). */
@Component
public class ArbitroConPartidosVerificador implements VerificadorEliminacion {

    private final PartidoRepository partidoRepository;

    public ArbitroConPartidosVerificador(PartidoRepository partidoRepository) {
        this.partidoRepository = partidoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Arbitro.class;
    }

    @Override
    public void verificar(Long id) {
        if (partidoRepository.existsByArbitroId(id)) {
            throw new ConflictoException("CONFLICTO_DEPENDENCIAS", "No se puede eliminar el árbitro: tiene partidos.");
        }
    }
}

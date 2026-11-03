package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/**
 * EST-02 (parte de los partidos): un estadio con partidos no se elimina. Lo consulta {@code EstadioService}
 * a través de {@code VerificadoresEliminacion} justo antes de borrar.
 */
@Component
public class EstadioConPartidosVerificador implements VerificadorEliminacion {

    private final PartidoRepository partidoRepository;

    public EstadioConPartidosVerificador(PartidoRepository partidoRepository) {
        this.partidoRepository = partidoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Estadio.class;
    }

    @Override
    public void verificar(Long id) {
        if (partidoRepository.existsByEstadioId(id)) {
            throw new ConflictoException("EST-02", "No se puede eliminar el estadio: tiene partidos.");
        }
    }
}

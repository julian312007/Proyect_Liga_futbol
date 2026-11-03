package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/**
 * EST-02 (parte de equipos): un estadio no se elimina si algún equipo lo tiene como estadio local (409).
 * La Fase 3 lo publica como bean; {@code EstadioService} (Fase 2) lo consulta a través de
 * {@code VerificadoresEliminacion} sin conocer a los equipos.
 */
@Component
public class EstadioConEquiposVerificador implements VerificadorEliminacion {

    static final String CODIGO = "EST-02";

    private final EquipoRepository equipoRepository;

    public EstadioConEquiposVerificador(EquipoRepository equipoRepository) {
        this.equipoRepository = equipoRepository;
    }

    @Override
    public Class<?> entidad() {
        return Estadio.class;
    }

    @Override
    public void verificar(Long id) {
        if (equipoRepository.existsByEstadioLocalId(id)) {
            throw new ConflictoException(CODIGO,
                    "No se puede eliminar el estadio: es el estadio local de al menos un equipo.");
        }
    }
}

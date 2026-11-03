package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.service.VerificadorEliminacion;
import org.springframework.stereotype.Component;

/**
 * Impide eliminar un jugador que tiene goles o tarjetas registrados (409, {@code CONFLICTO_DEPENDENCIAS}):
 * borrarlo dejaría eventos huérfanos o, con cascada, se perdería el historial del partido.
 * {@code JugadorService.eliminar} (Fase 3) lo consulta mediante {@code VerificadoresEliminacion}.
 */
@Component
public class JugadorConEventosVerificador implements VerificadorEliminacion {

    static final String CODIGO = "CONFLICTO_DEPENDENCIAS";

    private final GolRepository golRepository;
    private final TarjetaRepository tarjetaRepository;

    public JugadorConEventosVerificador(GolRepository golRepository, TarjetaRepository tarjetaRepository) {
        this.golRepository = golRepository;
        this.tarjetaRepository = tarjetaRepository;
    }

    @Override
    public Class<?> entidad() {
        return Jugador.class;
    }

    @Override
    public void verificar(Long id) {
        if (golRepository.existsByJugadorId(id) || tarjetaRepository.existsByJugadorId(id)) {
            throw new ConflictoException(CODIGO,
                    "No se puede eliminar el jugador %d: tiene goles o tarjetas registrados.".formatted(id));
        }
    }
}

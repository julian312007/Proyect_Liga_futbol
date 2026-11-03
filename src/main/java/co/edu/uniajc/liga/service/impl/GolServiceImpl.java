package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.GolMapper;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.service.GolService;
import co.edu.uniajc.liga.service.PaginacionSegura;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementación de {@link GolService}. Las reglas de estado del partido (RN-12), de pertenencia (RN-08) y
 * de suspensión (RN-10) las aplica {@link EventoPartidoValidator}; aquí vive el resto del flujo. Todo cambio
 * toma antes un bloqueo de escritura sobre el partido, así no puede entrar un gol mientras se finaliza.
 */
@Service
@Transactional
public class GolServiceImpl implements GolService {

    private static final Set<String> ORDENES_PERMITIDOS = Set.of("id", "minuto");
    private static final Sort ORDEN_POR_DEFECTO = Sort.by("minuto", "id");

    private final GolRepository golRepository;
    private final PartidoEventosRepository partidoRepository;
    private final EventoPartidoValidator validador;
    private final GolMapper golMapper;

    public GolServiceImpl(GolRepository golRepository,
                          PartidoEventosRepository partidoRepository,
                          EventoPartidoValidator validador,
                          GolMapper golMapper) {
        this.golRepository = golRepository;
        this.partidoRepository = partidoRepository;
        this.validador = validador;
        this.golMapper = golMapper;
    }

    @Override
    public GolResponse registrar(Long partidoId, GolRequest request) {
        Partido partido = validador.cargarPartidoEnJuego(partidoId);
        Jugador jugador = validador.cargarJugadorHabilitado(partido, request.jugadorId());

        Gol gol = golMapper.toEntity(request);
        gol.setPartido(partido);
        gol.setJugador(jugador);
        return golMapper.toResponse(golRepository.save(gol));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<GolResponse> listar(Long partidoId, Pageable pageable) {
        if (!partidoRepository.existsById(partidoId)) {
            throw new RecursoNoEncontradoException("Partido", partidoId);
        }
        Pageable seguro = PaginacionSegura.normalizar(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        return PaginaResponse.de(golRepository.findByPartidoId(partidoId, seguro).map(golMapper::toResponse));
    }

    @Override
    public void eliminar(Long partidoId, Long golId) {
        validador.cargarPartidoEnJuego(partidoId);
        Gol gol = golRepository.findByIdAndPartidoId(golId, partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gol", golId));
        golRepository.delete(gol);
    }
}

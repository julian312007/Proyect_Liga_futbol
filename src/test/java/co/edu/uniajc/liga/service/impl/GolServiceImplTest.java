package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.GolMapperImpl;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.service.SuspensionService;
import co.edu.uniajc.liga.support.FabricaEventosPartido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link GolServiceImpl} con Mockito (sin Spring ni Docker). El validador de eventos y el
 * mapper son los reales; los repositorios y {@code SuspensionService} se simulan. Cubre RN-08, RN-10 y RN-12
 * (cada una con caso positivo y negativo), el estado del partido y el listado/borrado de goles.
 */
@ExtendWith(MockitoExtension.class)
class GolServiceImplTest {

    private static final Long PARTIDO_ID = 50L;

    @Mock
    private GolRepository golRepository;
    @Mock
    private PartidoEventosRepository partidoRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private SuspensionService suspensionService;

    private GolServiceImpl servicio;

    private Equipo local;
    private Equipo visitante;
    private Jugador jugadorLocal;
    private Jugador jugadorVisitante;
    private Jugador jugadorAjeno;
    private Partido partido;

    @BeforeEach
    void preparar() {
        local = FabricaEventosPartido.equipo(1L, "Local FC");
        visitante = FabricaEventosPartido.equipo(2L, "Visitante FC");
        Equipo ajeno = FabricaEventosPartido.equipo(3L, "Ajeno FC");
        jugadorLocal = FabricaEventosPartido.jugador(101L, "Luis", local);
        jugadorVisitante = FabricaEventosPartido.jugador(201L, "Victor", visitante);
        jugadorAjeno = FabricaEventosPartido.jugador(301L, "Andres", ajeno);
        partido = FabricaEventosPartido.partido(PARTIDO_ID, FabricaEventosPartido.liga(10L), local, visitante,
                EstadoPartido.EN_JUEGO, Instant.parse("2026-02-01T20:00:00Z"));

        EventoPartidoValidator validador = new EventoPartidoValidator(partidoRepository, jugadorRepository,
                suspensionService);
        servicio = new GolServiceImpl(golRepository, partidoRepository, validador, new GolMapperImpl());
    }

    private void partidoExiste() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
    }

    private void guardarAsignaId() {
        when(golRepository.save(any(Gol.class))).thenAnswer(invocacion -> {
            Gol gol = invocacion.getArgument(0);
            gol.setId(900L);
            return gol;
        });
    }

    // ================================================================ RN-08

    @Test
    @DisplayName("RN-08 (positivo): un jugador del equipo local registra un gol y queda ligado al partido")
    void rn08_jugadorLocal_registraElGol() {
        partidoExiste();
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(jugadorLocal));
        when(suspensionService.estaSuspendido(101L, PARTIDO_ID)).thenReturn(false);
        guardarAsignaId();

        GolResponse respuesta = servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL));

        ArgumentCaptor<Gol> captor = ArgumentCaptor.forClass(Gol.class);
        verify(golRepository).save(captor.capture());
        assertThat(captor.getValue().getPartido()).isSameAs(partido);
        assertThat(captor.getValue().getJugador()).isSameAs(jugadorLocal);
        assertThat(captor.getValue().getMinuto()).isEqualTo(10);
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoGol.NORMAL);
        assertThat(respuesta.id()).isEqualTo(900L);
        assertThat(respuesta.partidoId()).isEqualTo(PARTIDO_ID);
        assertThat(respuesta.jugador()).isEqualTo(new ResumenResponse(101L, "Luis"));
        assertThat(respuesta.equipo()).isEqualTo(new ResumenResponse(1L, "Local FC"));
    }

    @Test
    @DisplayName("RN-08 (positivo): un jugador del equipo visitante también puede registrar un gol")
    void rn08_jugadorVisitante_registraElGol() {
        partidoExiste();
        when(jugadorRepository.findById(201L)).thenReturn(Optional.of(jugadorVisitante));
        when(suspensionService.estaSuspendido(201L, PARTIDO_ID)).thenReturn(false);
        guardarAsignaId();

        GolResponse respuesta = servicio.registrar(PARTIDO_ID, new GolRequest(201L, 30, TipoGol.PENAL));

        assertThat(respuesta.equipo()).isEqualTo(new ResumenResponse(2L, "Visitante FC"));
        assertThat(respuesta.tipo()).isEqualTo(TipoGol.PENAL);
    }

    @Test
    @DisplayName("RN-08 (negativo): un jugador de un tercer equipo no puede registrar un gol: 422 RN-08 y no se guarda")
    void rn08_jugadorDeOtroEquipo_lanza422() {
        partidoExiste();
        when(jugadorRepository.findById(301L)).thenReturn(Optional.of(jugadorAjeno));

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(301L, 10, TipoGol.NORMAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-08"));

        verify(golRepository, never()).save(any());
        verifyNoInteractions(suspensionService);
    }

    // ================================================================ RN-10

    @Test
    @DisplayName("RN-10 (negativo): un jugador suspendido por una roja previa no puede tener goles: 422 RN-10")
    void rn10_jugadorSuspendido_lanza422() {
        partidoExiste();
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(jugadorLocal));
        when(suspensionService.estaSuspendido(101L, PARTIDO_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-10"));

        verify(golRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN-10 (positivo): un jugador no suspendido sí registra su gol (se consulta a SuspensionService)")
    void rn10_jugadorNoSuspendido_registraElGol() {
        partidoExiste();
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(jugadorLocal));
        when(suspensionService.estaSuspendido(101L, PARTIDO_ID)).thenReturn(false);
        guardarAsignaId();

        servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL));

        verify(suspensionService).estaSuspendido(101L, PARTIDO_ID);
        verify(golRepository).save(any(Gol.class));
    }

    // ================================================================ RN-12 y estado

    @Test
    @DisplayName("RN-12 (negativo): con el partido FINALIZADO no se registran goles: 409 RN-12")
    void rn12_partidoFinalizado_lanza409() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoExiste();

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-12"));

        verify(golRepository, never()).save(any());
        verifyNoInteractions(jugadorRepository, suspensionService);
    }

    @Test
    @DisplayName("RN-12 (negativo): con el partido FINALIZADO tampoco se elimina un gol: 409 RN-12")
    void rn12_partidoFinalizado_noPermiteEliminar() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoExiste();

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO_ID, 7L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-12"));

        verify(golRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Con el partido PROGRAMADO no se registran goles: 409 PARTIDO_NO_EN_JUEGO")
    void partidoProgramado_lanza409() {
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partidoExiste();

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("PARTIDO_NO_EN_JUEGO"));

        verify(golRepository, never()).save(any());
    }

    @Test
    @DisplayName("Partido inexistente: 404 y no se consulta al jugador")
    void partidoInexistente_lanza404() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(101L, 10, TipoGol.NORMAL)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(jugadorRepository);
    }

    @Test
    @DisplayName("Jugador inexistente: 404 y no se guarda")
    void jugadorInexistente_lanza404() {
        partidoExiste();
        when(jugadorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new GolRequest(999L, 10, TipoGol.NORMAL)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(golRepository, never()).save(any());
    }

    // ================================================================ Eliminar

    @Test
    @DisplayName("Eliminar un gol del partido EN_JUEGO: se borra")
    void eliminar_golDelPartido_seBorra() {
        partidoExiste();
        Gol gol = FabricaEventosPartido.gol(7L, partido, jugadorLocal, 10, TipoGol.NORMAL);
        when(golRepository.findByIdAndPartidoId(7L, PARTIDO_ID)).thenReturn(Optional.of(gol));

        servicio.eliminar(PARTIDO_ID, 7L);

        verify(golRepository).delete(gol);
    }

    @Test
    @DisplayName("Eliminar un gol que no es de ese partido: 404 y no se borra nada")
    void eliminar_golDeOtroPartido_lanza404() {
        partidoExiste();
        when(golRepository.findByIdAndPartidoId(7L, PARTIDO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO_ID, 7L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(golRepository, never()).delete(any());
    }

    // ================================================================ Listar

    @Test
    @DisplayName("Listar: partido inexistente da 404")
    void listar_partidoInexistente_lanza404() {
        when(partidoRepository.existsById(PARTIDO_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listar(PARTIDO_ID, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(golRepository);
    }

    @Test
    @DisplayName("Listar: devuelve la página mapeada a GolResponse")
    void listar_devuelveLaPaginaMapeada() {
        when(partidoRepository.existsById(PARTIDO_ID)).thenReturn(true);
        Gol gol = FabricaEventosPartido.gol(7L, partido, jugadorLocal, 10, TipoGol.NORMAL);
        when(golRepository.findByPartidoId(eq(PARTIDO_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(gol), PageRequest.of(0, 20), 1));

        PaginaResponse<GolResponse> pagina = servicio.listar(PARTIDO_ID, PageRequest.of(0, 20));

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.contenido()).extracting(GolResponse::id).containsExactly(7L);
    }

    @Test
    @DisplayName("Listar: un orden no permitido (jugador.nombre) y un tamaño enorme se normalizan antes de consultar")
    void listar_normalizaElOrdenYElTamanio() {
        when(partidoRepository.existsById(PARTIDO_ID)).thenReturn(true);
        when(golRepository.findByPartidoId(eq(PARTIDO_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        servicio.listar(PARTIDO_ID, PageRequest.of(2, 100_000, Sort.by("jugador.nombre")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(golRepository).findByPartidoId(eq(PARTIDO_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("minuto", "id"));
    }

    // ================================================================ Transacciones

    @Test
    @DisplayName("El servicio es transaccional y el listado es de solo lectura")
    void transaccionalidad() throws NoSuchMethodException {
        assertThat(GolServiceImpl.class.getAnnotation(Transactional.class)).isNotNull();
        Transactional listado = GolServiceImpl.class
                .getMethod("listar", Long.class, Pageable.class).getAnnotation(Transactional.class);
        assertThat(listado).isNotNull();
        assertThat(listado.readOnly()).isTrue();
    }
}

package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.support.FabricaEventosPartido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * Prueba unitaria de {@link SuspensionServiceImpl} con Mockito (RN-10). Definición: suspendido en el
 * partido P = tuvo ROJA en el partido inmediatamente anterior de su equipo (misma liga, {@code fechaHora}
 * menor que la de P). El orden real de "el anterior" lo da la consulta JPQL del repositorio (se comprueba en
 * {@code EventosPartidoIT}); aquí se comprueba con qué argumentos se pide y qué se hace con la respuesta.
 *
 * <p>Escenario: liga 10; P (id 50) lo juegan Local (1) y Visitante (2). El partido anterior del local es
 * A1 (id 40, jornada 4) y el del visitante es A2 (id 41, jornada 4).
 */
@ExtendWith(MockitoExtension.class)
class SuspensionServiceImplTest {

    private static final Instant FECHA_P = Instant.parse("2026-02-08T20:00:00Z");

    @Mock
    private PartidoEventosRepository partidoRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private TarjetaRepository tarjetaRepository;

    private SuspensionServiceImpl servicio;

    private Liga liga;
    private Equipo local;
    private Equipo visitante;
    private Jugador luis;
    private Jugador victor;
    private Jugador ajeno;
    private Partido partidoP;
    private Partido anteriorLocal;
    private Partido anteriorVisitante;

    @BeforeEach
    void preparar() {
        liga = FabricaEventosPartido.liga(10L);
        local = FabricaEventosPartido.equipo(1L, "Local FC");
        visitante = FabricaEventosPartido.equipo(2L, "Visitante FC");
        Equipo otro = FabricaEventosPartido.equipo(3L, "Ajeno FC");
        luis = FabricaEventosPartido.jugador(101L, "Luis", local);
        victor = FabricaEventosPartido.jugador(201L, "Victor", visitante);
        ajeno = FabricaEventosPartido.jugador(301L, "Andres", otro);
        partidoP = FabricaEventosPartido.partido(50L, liga, local, visitante, EstadoPartido.EN_JUEGO, FECHA_P);
        anteriorLocal = FabricaEventosPartido.partido(40L, liga, local, otro, EstadoPartido.FINALIZADO,
                Instant.parse("2026-02-01T20:00:00Z"));
        anteriorLocal.setJornada(4);
        anteriorVisitante = FabricaEventosPartido.partido(41L, liga, otro, visitante, EstadoPartido.FINALIZADO,
                Instant.parse("2026-02-01T18:00:00Z"));
        anteriorVisitante.setJornada(4);
        servicio = new SuspensionServiceImpl(partidoRepository, jugadorRepository, tarjetaRepository);
    }

    private void anteriorDeEquipo(Long equipoId, Partido anterior) {
        when(partidoRepository.buscarAnterioresDeEquipo(eq(10L), eq(equipoId), eq(FECHA_P), any(Pageable.class)))
                .thenReturn(anterior == null ? List.of() : List.of(anterior));
    }

    // ================================================================ estaSuspendido

    @Test
    @DisplayName("RN-10 (positivo): con una roja en el partido anterior de su equipo, el jugador está suspendido")
    void rn10_conRojaEnElPartidoAnterior_estaSuspendido() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(luis));
        anteriorDeEquipo(1L, anteriorLocal);
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(40L, 101L, TipoTarjeta.ROJA)).thenReturn(true);

        assertThat(servicio.estaSuspendido(101L, 50L)).isTrue();
    }

    @Test
    @DisplayName("RN-10 (negativo): sin roja en el partido anterior de su equipo, el jugador no está suspendido")
    void rn10_sinRojaEnElPartidoAnterior_noEstaSuspendido() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(luis));
        anteriorDeEquipo(1L, anteriorLocal);
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(40L, 101L, TipoTarjeta.ROJA)).thenReturn(false);

        assertThat(servicio.estaSuspendido(101L, 50L)).isFalse();
    }

    @Test
    @DisplayName("RN-10 (negativo): si el equipo no tiene partido anterior en la liga, nadie está suspendido")
    void rn10_sinPartidoAnterior_noEstaSuspendido() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(luis));
        anteriorDeEquipo(1L, null);

        assertThat(servicio.estaSuspendido(101L, 50L)).isFalse();

        verifyNoInteractions(tarjetaRepository);
    }

    @Test
    @DisplayName("RN-10: usa el partido anterior del equipo DEL JUGADOR: para un visitante se busca el del visitante")
    void rn10_usaElPartidoAnteriorDeSuEquipo() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(201L)).thenReturn(Optional.of(victor));
        anteriorDeEquipo(2L, anteriorVisitante);
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(41L, 201L, TipoTarjeta.ROJA)).thenReturn(true);

        assertThat(servicio.estaSuspendido(201L, 50L)).isTrue();

        verify(partidoRepository, never()).buscarAnterioresDeEquipo(eq(10L), eq(1L), any(), any());
    }

    @Test
    @DisplayName("RN-10: el anterior se pide con la liga, la fecha del partido P y un límite de un resultado")
    void rn10_pideSoloElUltimoAnteriorAntesDeLaFechaDeP() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(luis));
        anteriorDeEquipo(1L, anteriorLocal);
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(40L, 101L, TipoTarjeta.ROJA)).thenReturn(false);

        servicio.estaSuspendido(101L, 50L);

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository).buscarAnterioresDeEquipo(eq(10L), eq(1L), eq(FECHA_P), limite.capture());
        assertThat(limite.getValue()).isEqualTo(PageRequest.of(0, 1));
    }

    @Test
    @DisplayName("Un jugador cuyo equipo no juega el partido no está suspendido en él (RN-08 lo rechaza aparte)")
    void jugadorDeOtroEquipo_noEstaSuspendido() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(301L)).thenReturn(Optional.of(ajeno));

        assertThat(servicio.estaSuspendido(301L, 50L)).isFalse();

        verify(partidoRepository, never()).buscarAnterioresDeEquipo(any(), any(), any(), any());
        verifyNoInteractions(tarjetaRepository);
    }

    @Test
    @DisplayName("estaSuspendido con partido o jugador inexistentes: 404")
    void estaSuspendido_inexistentes_lanza404() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.estaSuspendido(101L, 50L)).isInstanceOf(RecursoNoEncontradoException.class);

        when(partidoRepository.findById(51L)).thenReturn(Optional.of(partidoP));
        when(jugadorRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.estaSuspendido(999L, 51L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ================================================================ suspendidosEnPartido

    @Test
    @DisplayName("RN-10 (positivo): suspendidosEnPartido lista a los expulsados del partido anterior de cada equipo")
    void suspendidosEnPartido_listaLosExpulsadosDeCadaEquipo() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        anteriorDeEquipo(1L, anteriorLocal);
        anteriorDeEquipo(2L, anteriorVisitante);
        when(tarjetaRepository.findDeEquipoEnPartido(40L, 1L, TipoTarjeta.ROJA)).thenReturn(
                List.of(FabricaEventosPartido.tarjeta(1L, anteriorLocal, luis, TipoTarjeta.ROJA, 80)));
        when(tarjetaRepository.findDeEquipoEnPartido(41L, 2L, TipoTarjeta.ROJA)).thenReturn(
                List.of(FabricaEventosPartido.tarjeta(2L, anteriorVisitante, victor, TipoTarjeta.ROJA, 12)));

        List<SuspendidoResponse> suspendidos = servicio.suspendidosEnPartido(50L);

        assertThat(suspendidos).containsExactly(
                new SuspendidoResponse(101L, "Luis", 1L, "Local FC", 40L, 4),
                new SuspendidoResponse(201L, "Victor", 2L, "Visitante FC", 41L, 4));
    }

    @Test
    @DisplayName("RN-10 (negativo): sin rojas en los partidos anteriores, o sin partidos anteriores, la lista es vacía")
    void suspendidosEnPartido_sinRojas_listaVacia() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.of(partidoP));
        anteriorDeEquipo(1L, anteriorLocal);
        anteriorDeEquipo(2L, null);
        when(tarjetaRepository.findDeEquipoEnPartido(40L, 1L, TipoTarjeta.ROJA)).thenReturn(List.of());

        assertThat(servicio.suspendidosEnPartido(50L)).isEmpty();
    }

    @Test
    @DisplayName("suspendidosEnPartido con un partido inexistente: 404")
    void suspendidosEnPartido_partidoInexistente_lanza404() {
        when(partidoRepository.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.suspendidosEnPartido(50L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("El servicio es de solo lectura")
    void esDeSoloLectura() {
        Transactional transaccional = SuspensionServiceImpl.class.getAnnotation(Transactional.class);

        assertThat(transaccional).isNotNull();
        assertThat(transaccional.readOnly()).isTrue();
    }
}

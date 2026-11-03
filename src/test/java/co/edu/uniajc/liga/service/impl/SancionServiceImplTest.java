package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.SancionJugadorResponse;
import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import co.edu.uniajc.liga.repository.EstadisticasTarjetaRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.SuspensionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.jugador;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.tarjeta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Sanciones (F7): conteo de tarjetas y suspendidos por jornada (RN-10, con {@link SuspensionService} simulado). */
@ExtendWith(MockitoExtension.class)
class SancionServiceImplTest {

    private static final long LIGA = 7L;

    @Mock
    private LigaRepository ligaRepository;
    @Mock
    private EstadisticasTarjetaRepository tarjetaRepository;
    @Mock
    private EstadisticasPartidoRepository partidoRepository;
    @Mock
    private SuspensionService suspensionService;

    @InjectMocks
    private SancionServiceImpl servicio;

    private final Equipo aguilas = equipo(1, "Águilas");
    private final Equipo buhos = equipo(2, "Búhos");

    // ------------------------------------------------------------ Tarjetas

    @Test
    @DisplayName("Cuenta las amarillas y las rojas de cada jugador y omite a quien no tiene tarjetas")
    void cuentaAmarillasYRojasPorJugador() {
        Jugador ana = jugador(10, "Ana Ruiz", aguilas);
        Jugador luis = jugador(11, "Luis Mora", buhos);
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(tarjetaRepository.buscarDeLiga(LIGA)).thenReturn(List.of(
                tarjeta(1, ana, TipoTarjeta.AMARILLA),
                tarjeta(2, ana, TipoTarjeta.AMARILLA),
                tarjeta(3, ana, TipoTarjeta.ROJA),
                tarjeta(4, luis, TipoTarjeta.AMARILLA)));

        List<SancionJugadorResponse> sanciones = servicio.listarSanciones(LIGA);

        assertThat(sanciones).containsExactly(
                new SancionJugadorResponse(10L, "Ana Ruiz", 1L, "Águilas", 2, 1),
                new SancionJugadorResponse(11L, "Luis Mora", 2L, "Búhos", 1, 0));
    }

    @Test
    @DisplayName("Orden: más rojas primero, luego más amarillas y luego el nombre")
    void ordenaPorRojasAmarillasYNombre() {
        Jugador ana = jugador(10, "Ana Ruiz", aguilas);
        Jugador beto = jugador(11, "Beto Paz", aguilas);
        Jugador cris = jugador(12, "Cris Gil", buhos);
        Jugador dani = jugador(13, "Dani Sol", buhos);
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(tarjetaRepository.buscarDeLiga(LIGA)).thenReturn(List.of(
                tarjeta(1, dani, TipoTarjeta.AMARILLA),
                tarjeta(2, beto, TipoTarjeta.AMARILLA), tarjeta(3, beto, TipoTarjeta.AMARILLA),
                tarjeta(4, ana, TipoTarjeta.AMARILLA), tarjeta(5, ana, TipoTarjeta.AMARILLA),
                tarjeta(6, cris, TipoTarjeta.ROJA)));

        List<SancionJugadorResponse> sanciones = servicio.listarSanciones(LIGA);

        // Cris (1 roja) | Ana y Beto (2 amarillas, por nombre) | Dani (1 amarilla)
        assertThat(sanciones).extracting(SancionJugadorResponse::jugador)
                .containsExactly("Cris Gil", "Ana Ruiz", "Beto Paz", "Dani Sol");
    }

    @Test
    @DisplayName("Una liga sin tarjetas devuelve una lista vacía")
    void sinTarjetas_devuelveListaVacia() {
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(tarjetaRepository.buscarDeLiga(LIGA)).thenReturn(List.of());

        assertThat(servicio.listarSanciones(LIGA)).isEmpty();
    }

    @Test
    @DisplayName("Sanciones de una liga inexistente: RecursoNoEncontradoException (404)")
    void sanciones_ligaInexistente_lanza404() {
        when(ligaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listarSanciones(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verifyNoInteractions(tarjetaRepository);
    }

    // ---------------------------------------------------------- Suspendidos

    @Test
    @DisplayName("RN-10: los suspendidos de la jornada salen de SuspensionService para cada partido de esa jornada")
    void rn10_suspendidosDeLaJornada_consultaCadaPartido() {
        SuspendidoResponse deLosBuhos = new SuspendidoResponse(11L, "Luis Mora", 2L, "Búhos", 5L, 1);
        SuspendidoResponse deLasAguilas = new SuspendidoResponse(10L, "Ana Ruiz", 1L, "Águilas", 4L, 1);
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(partidoRepository.buscarIdsDeJornada(LIGA, 2)).thenReturn(List.of(100L, 101L));
        when(suspensionService.suspendidosEnPartido(100L)).thenReturn(List.of(deLosBuhos));
        when(suspensionService.suspendidosEnPartido(101L)).thenReturn(List.of(deLasAguilas));

        List<SuspendidoResponse> suspendidos = servicio.listarSuspendidos(LIGA, 2);

        // Se ordenan por equipo y nombre, no por el orden de los partidos.
        assertThat(suspendidos).containsExactly(deLasAguilas, deLosBuhos);
        verify(suspensionService).suspendidosEnPartido(100L);
        verify(suspensionService).suspendidosEnPartido(101L);
    }

    @Test
    @DisplayName("RN-10: un jugador que aparece en dos consultas de la jornada se lista una sola vez")
    void rn10_suspendidoRepetido_seListaUnaVez() {
        SuspendidoResponse repetido = new SuspendidoResponse(10L, "Ana Ruiz", 1L, "Águilas", 4L, 1);
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(partidoRepository.buscarIdsDeJornada(LIGA, 2)).thenReturn(List.of(100L, 101L));
        when(suspensionService.suspendidosEnPartido(100L)).thenReturn(List.of(repetido));
        when(suspensionService.suspendidosEnPartido(101L)).thenReturn(List.of(repetido));

        assertThat(servicio.listarSuspendidos(LIGA, 2)).containsExactly(repetido);
    }

    @Test
    @DisplayName("RN-10: una jornada sin partidos devuelve lista vacía y no consulta suspensiones")
    void rn10_jornadaSinPartidos_devuelveListaVacia() {
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(partidoRepository.buscarIdsDeJornada(LIGA, 30)).thenReturn(List.of());

        assertThat(servicio.listarSuspendidos(LIGA, 30)).isEmpty();

        verifyNoInteractions(suspensionService);
    }

    @Test
    @DisplayName("RN-10: si nadie está suspendido en los partidos de la jornada, la lista es vacía")
    void rn10_sinSuspendidos_devuelveListaVacia() {
        when(ligaRepository.existsById(LIGA)).thenReturn(true);
        when(partidoRepository.buscarIdsDeJornada(LIGA, 1)).thenReturn(List.of(100L));
        when(suspensionService.suspendidosEnPartido(100L)).thenReturn(List.of());

        assertThat(servicio.listarSuspendidos(LIGA, 1)).isEmpty();
    }

    @Test
    @DisplayName("Suspendidos de una liga inexistente: RecursoNoEncontradoException (404)")
    void suspendidos_ligaInexistente_lanza404() {
        when(ligaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listarSuspendidos(99L, 1))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(partidoRepository, suspensionService);
    }
}

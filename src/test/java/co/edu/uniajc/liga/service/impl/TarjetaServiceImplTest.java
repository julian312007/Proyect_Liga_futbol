package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.ResumenResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.TarjetaMapperImpl;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link TarjetaServiceImpl} con Mockito (sin Spring ni Docker). Cubre RN-09 (la segunda
 * amarilla genera la roja en el mismo minuto y la misma transacción), el rechazo de tarjetas a un expulsado
 * ({@code TARJETA_JUGADOR_EXPULSADO}), RN-08, RN-10 y RN-12, cada una con caso positivo y negativo.
 */
@ExtendWith(MockitoExtension.class)
class TarjetaServiceImplTest {

    private static final Long PARTIDO_ID = 50L;

    @Mock
    private TarjetaRepository tarjetaRepository;
    @Mock
    private PartidoEventosRepository partidoRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private SuspensionService suspensionService;

    private TarjetaServiceImpl servicio;

    private Jugador jugadorLocal;
    private Jugador jugadorAjeno;
    private Partido partido;
    private final AtomicLong secuencia = new AtomicLong(1000);

    @BeforeEach
    void preparar() {
        Equipo local = FabricaEventosPartido.equipo(1L, "Local FC");
        Equipo visitante = FabricaEventosPartido.equipo(2L, "Visitante FC");
        Equipo ajeno = FabricaEventosPartido.equipo(3L, "Ajeno FC");
        jugadorLocal = FabricaEventosPartido.jugador(101L, "Luis", local);
        jugadorAjeno = FabricaEventosPartido.jugador(301L, "Andres", ajeno);
        partido = FabricaEventosPartido.partido(PARTIDO_ID, FabricaEventosPartido.liga(10L), local, visitante,
                EstadoPartido.EN_JUEGO, Instant.parse("2026-02-01T20:00:00Z"));

        EventoPartidoValidator validador = new EventoPartidoValidator(partidoRepository, jugadorRepository,
                suspensionService);
        servicio = new TarjetaServiceImpl(tarjetaRepository, partidoRepository, validador, new TarjetaMapperImpl());
    }

    /** Partido EN_JUEGO, jugador local existente y no suspendido: el camino feliz hasta las reglas de tarjeta. */
    private void escenarioBase() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(jugadorLocal));
        when(suspensionService.estaSuspendido(101L, PARTIDO_ID)).thenReturn(false);
    }

    private void guardarAsignaId() {
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(invocacion -> {
            Tarjeta tarjeta = invocacion.getArgument(0);
            tarjeta.setId(secuencia.incrementAndGet());
            return tarjeta;
        });
    }

    // ================================================================ RN-09

    @Test
    @DisplayName("RN-09 (negativo): la primera amarilla se guarda sola, sin roja")
    void rn09_primeraAmarilla_noGeneraRoja() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(false);
        when(tarjetaRepository.countByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.AMARILLA)).thenReturn(0L);
        guardarAsignaId();

        TarjetaResponse respuesta = servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 20));

        ArgumentCaptor<Tarjeta> captor = ArgumentCaptor.forClass(Tarjeta.class);
        verify(tarjetaRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoTarjeta.AMARILLA);
        assertThat(captor.getValue().getMinuto()).isEqualTo(20);
        assertThat(respuesta.tipo()).isEqualTo(TipoTarjeta.AMARILLA);
        assertThat(respuesta.jugador()).isEqualTo(new ResumenResponse(101L, "Luis"));
    }

    @Test
    @DisplayName("RN-09 (positivo): la segunda amarilla crea además una ROJA en el mismo minuto, del mismo jugador y partido")
    void rn09_segundaAmarilla_creaRojaEnElMismoMinuto() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(false);
        when(tarjetaRepository.countByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.AMARILLA)).thenReturn(1L);
        guardarAsignaId();

        TarjetaResponse respuesta = servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 67));

        ArgumentCaptor<Tarjeta> captor = ArgumentCaptor.forClass(Tarjeta.class);
        verify(tarjetaRepository, times(2)).save(captor.capture());
        Tarjeta amarilla = captor.getAllValues().get(0);
        Tarjeta roja = captor.getAllValues().get(1);
        assertThat(amarilla.getTipo()).isEqualTo(TipoTarjeta.AMARILLA);
        assertThat(roja.getTipo()).isEqualTo(TipoTarjeta.ROJA);
        assertThat(roja.getMinuto()).isEqualTo(67).isEqualTo(amarilla.getMinuto());
        assertThat(roja.getJugador()).isSameAs(jugadorLocal);
        assertThat(roja.getPartido()).isSameAs(partido);
        // La respuesta es la amarilla pedida; la roja automática se ve en el listado.
        assertThat(respuesta.tipo()).isEqualTo(TipoTarjeta.AMARILLA);
    }

    @Test
    @DisplayName("RN-09 (positivo): si la roja se borró y el jugador ya tiene dos amarillas, otra amarilla vuelve a generar la roja")
    void rn09_conDosAmarillasSinRoja_otraAmarillaGeneraRoja() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(false);
        when(tarjetaRepository.countByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.AMARILLA)).thenReturn(2L);
        guardarAsignaId();

        servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 80));

        ArgumentCaptor<Tarjeta> captor = ArgumentCaptor.forClass(Tarjeta.class);
        verify(tarjetaRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Tarjeta::getTipo)
                .containsExactly(TipoTarjeta.AMARILLA, TipoTarjeta.ROJA);
    }

    @Test
    @DisplayName("RN-09 (negativo): una ROJA directa se guarda sola y no se cuentan las amarillas")
    void rn09_rojaDirecta_seGuardaSola() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(false);
        guardarAsignaId();

        TarjetaResponse respuesta = servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.ROJA, 55));

        verify(tarjetaRepository, times(1)).save(any(Tarjeta.class));
        verify(tarjetaRepository, never()).countByPartidoIdAndJugadorIdAndTipo(any(), any(), any());
        assertThat(respuesta.tipo()).isEqualTo(TipoTarjeta.ROJA);
    }

    @Test
    @DisplayName("RN-09 (positivo): una amarilla tras una ROJA directa previa no genera otra roja (ni se admite)")
    void rn09_amarillaDespuesDeRojaDirecta_seRechaza() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 80)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("TARJETA_JUGADOR_EXPULSADO"));

        verify(tarjetaRepository, never()).save(any());
    }

    // ============================================== Jugador ya expulsado en el partido

    @Test
    @DisplayName("TARJETA_JUGADOR_EXPULSADO (negativo): a un jugador expulsado en el partido no se le admite otra tarjeta: 422")
    void expulsado_noAdmiteNuevasTarjetas() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.ROJA, 90)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("TARJETA_JUGADOR_EXPULSADO"));

        verify(tarjetaRepository, never()).save(any());
    }

    // ================================================================ RN-08

    @Test
    @DisplayName("RN-08 (negativo): un jugador de un tercer equipo no puede recibir tarjetas: 422 RN-08")
    void rn08_jugadorDeOtroEquipo_lanza422() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        when(jugadorRepository.findById(301L)).thenReturn(Optional.of(jugadorAjeno));

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(301L, TipoTarjeta.AMARILLA, 10)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-08"));

        verify(tarjetaRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN-08 (positivo): un jugador de uno de los dos equipos recibe su tarjeta")
    void rn08_jugadorDelPartido_recibeLaTarjeta() {
        escenarioBase();
        when(tarjetaRepository.existsByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.ROJA)).thenReturn(false);
        when(tarjetaRepository.countByPartidoIdAndJugadorIdAndTipo(PARTIDO_ID, 101L, TipoTarjeta.AMARILLA)).thenReturn(0L);
        guardarAsignaId();

        servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 10));

        verify(tarjetaRepository).save(any(Tarjeta.class));
    }

    // ================================================================ RN-10

    @Test
    @DisplayName("RN-10 (negativo): un jugador suspendido no puede recibir tarjetas: 422 RN-10")
    void rn10_jugadorSuspendido_lanza422() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        when(jugadorRepository.findById(101L)).thenReturn(Optional.of(jugadorLocal));
        when(suspensionService.estaSuspendido(101L, PARTIDO_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 10)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-10"));

        verify(tarjetaRepository, never()).save(any());
    }

    // ================================================================ RN-12 y estado

    @Test
    @DisplayName("RN-12 (negativo): con el partido FINALIZADO no se registran tarjetas: 409 RN-12")
    void rn12_partidoFinalizado_lanza409() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 10)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-12"));

        verifyNoInteractions(jugadorRepository, suspensionService);
        verify(tarjetaRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN-12 (negativo): con el partido FINALIZADO tampoco se elimina una tarjeta: 409 RN-12")
    void rn12_partidoFinalizado_noPermiteEliminar() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO_ID, 9L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-12"));

        verify(tarjetaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Con el partido PROGRAMADO no se registran tarjetas: 409 PARTIDO_NO_EN_JUEGO")
    void partidoProgramado_lanza409() {
        partido.setEstado(EstadoPartido.PROGRAMADO);
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 10)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("PARTIDO_NO_EN_JUEGO"));
    }

    @Test
    @DisplayName("Partido inexistente: 404")
    void partidoInexistente_lanza404() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PARTIDO_ID, new TarjetaRequest(101L, TipoTarjeta.AMARILLA, 10)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ================================================================ Eliminar y listar

    @Test
    @DisplayName("Eliminar una tarjeta del partido EN_JUEGO: se borra")
    void eliminar_tarjetaDelPartido_seBorra() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        Tarjeta tarjeta = FabricaEventosPartido.tarjeta(9L, partido, jugadorLocal, TipoTarjeta.AMARILLA, 10);
        when(tarjetaRepository.findByIdAndPartidoId(9L, PARTIDO_ID)).thenReturn(Optional.of(tarjeta));

        servicio.eliminar(PARTIDO_ID, 9L);

        verify(tarjetaRepository).delete(tarjeta);
    }

    @Test
    @DisplayName("Eliminar una tarjeta que no es de ese partido: 404 y no se borra nada")
    void eliminar_tarjetaDeOtroPartido_lanza404() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
        when(tarjetaRepository.findByIdAndPartidoId(9L, PARTIDO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO_ID, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(tarjetaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Listar: partido inexistente da 404")
    void listar_partidoInexistente_lanza404() {
        when(partidoRepository.existsById(PARTIDO_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listar(PARTIDO_ID, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(tarjetaRepository);
    }

    @Test
    @DisplayName("Listar: devuelve la página mapeada y normaliza un orden no permitido")
    void listar_devuelveLaPaginaMapeada() {
        when(partidoRepository.existsById(PARTIDO_ID)).thenReturn(true);
        Tarjeta tarjeta = FabricaEventosPartido.tarjeta(9L, partido, jugadorLocal, TipoTarjeta.ROJA, 70);
        when(tarjetaRepository.findByPartidoId(eq(PARTIDO_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tarjeta), PageRequest.of(0, 20), 1));

        PaginaResponse<TarjetaResponse> pagina =
                servicio.listar(PARTIDO_ID, PageRequest.of(0, 20, Sort.by("jugador.nombre")));

        assertThat(pagina.contenido()).extracting(TarjetaResponse::id).containsExactly(9L);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tarjetaRepository).findByPartidoId(eq(PARTIDO_ID), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("minuto", "id"));
    }

    // ================================================================ Misma transacción (RN-09)

    @Test
    @DisplayName("RN-09: registrar corre en UNA transacción de escritura (la amarilla y la roja se guardan juntas o ninguna)")
    void rn09_registrarEsTransaccionalDeEscritura() throws NoSuchMethodException {
        Transactional enClase = TarjetaServiceImpl.class.getAnnotation(Transactional.class);
        Transactional enMetodo = TarjetaServiceImpl.class
                .getMethod("registrar", Long.class, TarjetaRequest.class).getAnnotation(Transactional.class);

        assertThat(enClase).isNotNull();
        assertThat(enClase.readOnly()).isFalse();
        assertThat(enMetodo).isNull(); // Hereda la de la clase: una sola transacción para todo el método.
    }
}

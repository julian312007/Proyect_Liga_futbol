package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.ResultadoPartidoMapperImpl;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.PartidoEventosRepository;
import co.edu.uniajc.liga.support.FabricaEventosPartido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link CierrePartidoServiceImpl} con Mockito (sin Spring ni Docker). Cubre RN-11 con
 * marcadores calculados a mano (incluidos los autogoles) y RN-12 (un partido finalizado no se vuelve a
 * finalizar). La invalidación real de la caché se prueba en {@code CierrePartidoCacheTest}.
 *
 * <p>Equipos de las pruebas: local (id 1) con los jugadores L1 y L2; visitante (id 2) con V1 y V2.
 */
@ExtendWith(MockitoExtension.class)
class CierrePartidoServiceImplTest {

    private static final Long PARTIDO_ID = 50L;

    private static final Equipo LOCAL = FabricaEventosPartido.equipo(1L, "Local FC");
    private static final Equipo VISITANTE = FabricaEventosPartido.equipo(2L, "Visitante FC");
    private static final Jugador L1 = FabricaEventosPartido.jugador(101L, "L1", LOCAL);
    private static final Jugador L2 = FabricaEventosPartido.jugador(102L, "L2", LOCAL);
    private static final Jugador V1 = FabricaEventosPartido.jugador(201L, "V1", VISITANTE);
    private static final Jugador V2 = FabricaEventosPartido.jugador(202L, "V2", VISITANTE);

    @Mock
    private PartidoEventosRepository partidoRepository;
    @Mock
    private GolRepository golRepository;

    private CierrePartidoServiceImpl servicio;
    private Partido partido;

    @BeforeEach
    void preparar() {
        partido = FabricaEventosPartido.partido(PARTIDO_ID, FabricaEventosPartido.liga(10L), LOCAL, VISITANTE,
                EstadoPartido.EN_JUEGO, Instant.parse("2026-02-01T20:00:00Z"));
        servicio = new CierrePartidoServiceImpl(partidoRepository, golRepository, new ResultadoPartidoMapperImpl());
    }

    private void partidoExiste() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.of(partido));
    }

    private static Gol gol(Jugador jugador, int minuto, TipoGol tipo) {
        return FabricaEventosPartido.gol(null, null, jugador, minuto, tipo);
    }

    /**
     * Casos calculados a mano: nombre, goles registrados, marcador esperado local y visitante.
     * Regla: cada equipo suma sus NORMAL y PENAL más los AUTOGOL del rival.
     */
    static Stream<Arguments> marcadoresCalculadosAMano() {
        return Stream.of(
                // Sin goles: 0-0.
                Arguments.of("sin goles: 0-0", List.<Gol>of(), 0, 0),
                // Local: 2 NORMAL + 1 PENAL = 3. Visitante: 1 NORMAL = 1.
                Arguments.of("goles normales y penal: 3-1",
                        List.of(gol(L1, 10, TipoGol.NORMAL), gol(L2, 20, TipoGol.NORMAL), gol(L1, 30, TipoGol.PENAL),
                                gol(V1, 40, TipoGol.NORMAL)), 3, 1),
                // Un autogol de V1 suma al LOCAL: 1-0 (y no 0-1).
                Arguments.of("un autogol del visitante suma al local: 1-0",
                        List.of(gol(V1, 15, TipoGol.AUTOGOL)), 1, 0),
                // Un autogol de L1 suma al VISITANTE: 0-1.
                Arguments.of("un autogol del local suma al visitante: 0-1",
                        List.of(gol(L1, 15, TipoGol.AUTOGOL)), 0, 1),
                // Local: L1 NORMAL = 1. Visitante: V1 NORMAL = 1 + autogol de L2 = 2. Resultado 1-2.
                Arguments.of("gol normal de cada uno y un autogol del local: 1-2",
                        List.of(gol(L1, 10, TipoGol.NORMAL), gol(V1, 30, TipoGol.NORMAL), gol(L2, 50, TipoGol.AUTOGOL)), 1, 2),
                // Local: L1 NORMAL + L1 PENAL + 2 autogoles de V1/V2 = 4. Visitante: V1 NORMAL + autogol de L2 = 2.
                Arguments.of("mezcla con dos autogoles del visitante y uno del local: 4-2",
                        List.of(gol(L1, 5, TipoGol.NORMAL), gol(L1, 25, TipoGol.PENAL), gol(L2, 35, TipoGol.AUTOGOL),
                                gol(V1, 45, TipoGol.AUTOGOL), gol(V2, 60, TipoGol.AUTOGOL), gol(V1, 90, TipoGol.NORMAL)), 4, 2),
                // Un autogol de cada uno: 1-1.
                Arguments.of("un autogol de cada equipo: 1-1",
                        List.of(gol(L1, 12, TipoGol.AUTOGOL), gol(V2, 77, TipoGol.AUTOGOL)), 1, 1));
    }

    // ================================================================ RN-11

    @ParameterizedTest(name = "RN-11 (positivo): {0}")
    @MethodSource("marcadoresCalculadosAMano")
    @DisplayName("RN-11 (positivo): con el marcador correcto el partido se finaliza y guarda el marcador")
    void rn11_marcadorCorrecto_finaliza(String nombre, List<Gol> goles, int local, int visitante) {
        partidoExiste();
        when(golRepository.findConEquipoByPartidoId(PARTIDO_ID)).thenReturn(goles);

        ResultadoPartidoResponse resultado = servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(local, visitante));

        assertThat(resultado).isEqualTo(new ResultadoPartidoResponse(PARTIDO_ID, 10L, local, visitante,
                EstadoPartido.FINALIZADO));
        assertThat(partido.getEstado()).isEqualTo(EstadoPartido.FINALIZADO);
        assertThat(partido.getGolesLocal()).isEqualTo(local);
        assertThat(partido.getGolesVisitante()).isEqualTo(visitante);
        verify(partidoRepository).save(partido);
    }

    @ParameterizedTest(name = "RN-11 (negativo): {0}, pero se envía el marcador invertido o con un gol de más")
    @MethodSource("marcadoresCalculadosAMano")
    @DisplayName("RN-11 (negativo): con un marcador que no coincide se rechaza con 422 y el partido no cambia")
    void rn11_marcadorIncorrecto_lanza422(String nombre, List<Gol> goles, int local, int visitante) {
        partidoExiste();
        when(golRepository.findConEquipoByPartidoId(PARTIDO_ID)).thenReturn(goles);

        // Un gol de más para el local siempre es incorrecto.
        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(local + 1, visitante)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-11"));

        assertThat(partido.getEstado()).isEqualTo(EstadoPartido.EN_JUEGO);
        assertThat(partido.getGolesLocal()).isZero();
        assertThat(partido.getGolesVisitante()).isZero();
        verify(partidoRepository, never()).save(partido);
    }

    @Test
    @DisplayName("RN-11 (negativo): el autogol NO suma al equipo del que lo marca: contarlo así daría 2-1 y se rechaza")
    void rn11_autogolNoSumaAlPropioEquipo() {
        partidoExiste();
        // Goles: L1 normal, V1 normal, L2 autogol. Correcto: local 1, visitante 2. Lectura errónea: 2-1.
        when(golRepository.findConEquipoByPartidoId(PARTIDO_ID)).thenReturn(
                List.of(gol(L1, 10, TipoGol.NORMAL), gol(V1, 30, TipoGol.NORMAL), gol(L2, 50, TipoGol.AUTOGOL)));

        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(2, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-11"));
    }

    // ================================================================ RN-12 y estado

    @Test
    @DisplayName("RN-12 (negativo): un partido ya FINALIZADO no se vuelve a finalizar: 409 RN-12 y no se consultan los goles")
    void rn12_partidoFinalizado_lanza409() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        partido.setGolesLocal(2);
        partido.setGolesVisitante(0);
        partidoExiste();

        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(5, 5)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RN-12"));

        assertThat(partido.getGolesLocal()).isEqualTo(2);
        assertThat(partido.getGolesVisitante()).isZero();
        verifyNoInteractions(golRepository);
        verify(partidoRepository, never()).save(partido);
    }

    @Test
    @DisplayName("RN-12 (positivo): un partido EN_JUEGO sí se puede finalizar (una sola vez)")
    void rn12_partidoEnJuego_seFinaliza() {
        partidoExiste();
        when(golRepository.findConEquipoByPartidoId(PARTIDO_ID)).thenReturn(List.of());

        ResultadoPartidoResponse resultado = servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(0, 0));

        assertThat(resultado.estado()).isEqualTo(EstadoPartido.FINALIZADO);
    }

    @Test
    @DisplayName("Finalizar solo desde EN_JUEGO: un partido PROGRAMADO da 409 PARTIDO_ESTADO_INVALIDO")
    void finalizar_partidoProgramado_lanza409() {
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partidoExiste();

        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(0, 0)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("PARTIDO_ESTADO_INVALIDO"));

        assertThat(partido.getEstado()).isEqualTo(EstadoPartido.PROGRAMADO);
        verifyNoInteractions(golRepository);
    }

    @Test
    @DisplayName("Finalizar un partido inexistente: 404")
    void finalizar_partidoInexistente_lanza404() {
        when(partidoRepository.buscarBloqueadoPorId(PARTIDO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.finalizar(PARTIDO_ID, new FinalizarPartidoRequest(0, 0)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ================================================================ Caché

    @Test
    @DisplayName("finalizar declara @CacheEvict sobre la tabla de posiciones y los goleadores, con la liga como clave")
    void finalizar_declaraElCacheEvictDelContrato() throws NoSuchMethodException {
        CacheEvict anotacion = CierrePartidoServiceImpl.class
                .getMethod("finalizar", Long.class, FinalizarPartidoRequest.class).getAnnotation(CacheEvict.class);

        assertThat(anotacion).isNotNull();
        assertThat(anotacion.cacheNames()).containsExactlyInAnyOrder(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);
        assertThat(anotacion.key()).isEqualTo("#result.ligaId()");
        assertThat(anotacion.beforeInvocation()).isFalse();

        // La expresión de la clave es válida y da el id de la liga del resultado.
        StandardEvaluationContext contexto = new StandardEvaluationContext();
        contexto.setVariable("result", new ResultadoPartidoResponse(50L, 10L, 1, 0, EstadoPartido.FINALIZADO));
        Object clave = new SpelExpressionParser().parseExpression(anotacion.key()).getValue(contexto);
        assertThat(clave).isEqualTo(10L);
    }
}

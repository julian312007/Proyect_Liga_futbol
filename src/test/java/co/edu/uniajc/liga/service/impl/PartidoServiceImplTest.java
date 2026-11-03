package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.PartidoMapperImpl;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.repository.PartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de {@link PartidoServiceImpl} con Mockito (sin Spring ni Docker). El mapper es el real
 * generado por MapStruct; los repositorios se simulan. Cada regla (RN-04 a RN-07 y la parte del partido de
 * RN-12) tiene su caso positivo y su caso negativo, y el nombre de la prueba lleva el código de la regla.
 */
@ExtendWith(MockitoExtension.class)
class PartidoServiceImplTest {

    private static final long SIN_EXCLUSION = PartidoRepository.SIN_EXCLUSION;
    private static final Instant HORA = Instant.parse("2026-02-01T20:00:00-05:00");

    private static final long LIGA = 1L;
    private static final long LOCAL = 10L;
    private static final long VISITANTE = 11L;
    private static final long ESTADIO = 20L;
    private static final long ARBITRO = 30L;
    private static final long PARTIDO = 7L;

    @Mock
    private PartidoRepository partidoRepository;
    @Mock
    private LigaRepository ligaRepository;
    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private EstadioRepository estadioRepository;
    @Mock
    private ArbitroRepository arbitroRepository;

    private PartidoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = servicioConVentana(PartidoServiceImpl.VENTANA_SOLAPE);
        // Catálogos existentes por defecto; cada prueba que necesita lo contrario lo redefine.
        lenient().when(ligaRepository.findById(LIGA)).thenReturn(Optional.of(liga()));
        lenient().when(equipoRepository.findById(LOCAL)).thenReturn(Optional.of(equipo(LOCAL, "Cali FC")));
        lenient().when(equipoRepository.findById(VISITANTE)).thenReturn(Optional.of(equipo(VISITANTE, "Medellín FC")));
        lenient().when(estadioRepository.findById(ESTADIO)).thenReturn(Optional.of(estadio()));
        lenient().when(arbitroRepository.findById(ARBITRO)).thenReturn(Optional.of(arbitro()));
        // Guardar asigna un id, como lo haría la base de datos.
        lenient().when(partidoRepository.saveAndFlush(any(Partido.class))).thenAnswer(invocacion -> {
            Partido guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) {
                guardado.setId(55L);
            }
            return guardado;
        });
        lenient().when(partidoRepository.save(any(Partido.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ========================================================================== Crear

    @Test
    @DisplayName("Crear un partido válido: queda PROGRAMADO 0-0, con sus cinco relaciones y la respuesta resumida")
    void crear_partidoValido_quedaProgramado() {
        PartidoResponse respuesta = servicio.crear(solicitud());

        ArgumentCaptor<Partido> captor = ArgumentCaptor.forClass(Partido.class);
        verify(partidoRepository).saveAndFlush(captor.capture());
        Partido guardado = captor.getValue();
        assertThat(guardado.getEstado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(guardado.getGolesLocal()).isZero();
        assertThat(guardado.getGolesVisitante()).isZero();
        assertThat(guardado.getJornada()).isEqualTo(3);
        assertThat(guardado.getFechaHora()).isEqualTo(HORA);
        assertThat(guardado.getLiga().getId()).isEqualTo(LIGA);
        assertThat(guardado.getEquipoLocal().getId()).isEqualTo(LOCAL);
        assertThat(guardado.getEquipoVisitante().getId()).isEqualTo(VISITANTE);
        assertThat(guardado.getEstadio().getId()).isEqualTo(ESTADIO);
        assertThat(guardado.getArbitro().getId()).isEqualTo(ARBITRO);

        assertThat(respuesta.id()).isEqualTo(55L);
        assertThat(respuesta.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(respuesta.equipoLocal().nombre()).isEqualTo("Cali FC");
        assertThat(respuesta.estadio().nombre()).isEqualTo("Pascual Guerrero");
    }

    @ParameterizedTest(name = "falta {0}")
    @ValueSource(strings = {"liga", "local", "visitante", "estadio", "arbitro"})
    @DisplayName("Crear con una liga, equipo, estadio o árbitro que no existe: 404 y no se guarda nada")
    void crear_relacionInexistente_lanza404(String faltante) {
        switch (faltante) {
            case "liga" -> when(ligaRepository.findById(LIGA)).thenReturn(Optional.empty());
            case "local" -> when(equipoRepository.findById(LOCAL)).thenReturn(Optional.empty());
            case "visitante" -> when(equipoRepository.findById(VISITANTE)).thenReturn(Optional.empty());
            case "estadio" -> when(estadioRepository.findById(ESTADIO)).thenReturn(Optional.empty());
            default -> when(arbitroRepository.findById(ARBITRO)).thenReturn(Optional.empty());
        }

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    // ===================================================================== RN-04

    @Test
    @DisplayName("RN-04 (negativo): un equipo contra sí mismo es 422 y ni siquiera se consulta la base de datos")
    void rn04_equipoContraSiMismo_lanza422() {
        PartidoRequest consigoMismo = new PartidoRequest(LIGA, 3, HORA, LOCAL, LOCAL, ESTADIO, ARBITRO);

        assertThatThrownBy(() -> servicio.crear(consigoMismo))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-04"));

        verifyNoInteractions(partidoRepository, ligaRepository, equipoRepository, estadioRepository, arbitroRepository);
    }

    @Test
    @DisplayName("RN-04 (negativo): también se rechaza al reprogramar un partido para que un equipo se enfrente a sí mismo")
    void rn04_alEditar_equipoContraSiMismo_lanza422() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.PROGRAMADO)));
        PartidoRequest consigoMismo = new PartidoRequest(LIGA, 3, HORA, VISITANTE, VISITANTE, ESTADIO, ARBITRO);

        assertThatThrownBy(() -> servicio.actualizar(PARTIDO, consigoMismo))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-04"));

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("RN-04 (positivo): dos equipos distintos se enfrentan sin problema")
    void rn04_equiposDistintos_seProgramaElPartido() {
        assertThatCode(() -> servicio.crear(solicitud())).doesNotThrowAnyException();

        verify(partidoRepository).saveAndFlush(any(Partido.class));
    }

    // ===================================================================== RN-05

    @Test
    @DisplayName("RN-05 (negativo): si el equipo local ya juega en esa jornada de la liga, 409")
    void rn05_localYaJuegaEnLaJornada_lanza409() {
        when(partidoRepository.contarPartidosDelEquipoEnJornada(LIGA, 3, LOCAL, SIN_EXCLUSION)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("RN-05");
                    assertThat(ex.getMessage()).contains("Cali FC").contains("jornada 3");
                });

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("RN-05 (negativo): si el equipo visitante ya juega en esa jornada de la liga (aunque sea de local), 409")
    void rn05_visitanteYaJuegaEnLaJornada_lanza409() {
        when(partidoRepository.contarPartidosDelEquipoEnJornada(LIGA, 3, LOCAL, SIN_EXCLUSION)).thenReturn(0L);
        when(partidoRepository.contarPartidosDelEquipoEnJornada(LIGA, 3, VISITANTE, SIN_EXCLUSION)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("RN-05");
                    assertThat(ex.getMessage()).contains("Medellín FC");
                });
    }

    @Test
    @DisplayName("RN-05 (positivo): equipos sin partido en la jornada se programan; se consultó a los dos equipos")
    void rn05_equiposLibresEnLaJornada_seProgramaElPartido() {
        servicio.crear(solicitud());

        verify(partidoRepository).contarPartidosDelEquipoEnJornada(LIGA, 3, LOCAL, SIN_EXCLUSION);
        verify(partidoRepository).contarPartidosDelEquipoEnJornada(LIGA, 3, VISITANTE, SIN_EXCLUSION);
        verify(partidoRepository).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("RN-05 (positivo): al editar se excluye el propio partido, que no choca consigo mismo")
    void rn05_alEditar_seExcluyeElPropioPartido() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.PROGRAMADO)));

        servicio.actualizar(PARTIDO, solicitud());

        verify(partidoRepository).contarPartidosDelEquipoEnJornada(LIGA, 3, LOCAL, PARTIDO);
        verify(partidoRepository).contarPartidosDelEquipoEnJornada(LIGA, 3, VISITANTE, PARTIDO);
        verify(partidoRepository, never()).contarPartidosDelEquipoEnJornada(any(), any(), any(), eq(SIN_EXCLUSION));
    }

    // ===================================================================== RN-06

    @Test
    @DisplayName("RN-06 (negativo): el estadio ya tiene un partido a la misma fecha y hora exactas, 409")
    void rn06_estadioOcupadoALaMismaHora_lanza409() {
        when(partidoRepository.horariosOcupadosDelEstadio(eq(ESTADIO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA));

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("RN-06");
                    assertThat(ex.getMessage()).contains("Pascual Guerrero");
                });

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("RN-06 (positivo): el estadio tiene otro partido, pero a otra hora, y con la ventana en cero no choca")
    void rn06_estadioConOtraHora_seProgramaElPartido() {
        when(partidoRepository.horariosOcupadosDelEstadio(eq(ESTADIO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA.plus(Duration.ofMinutes(30))));

        assertThatCode(() -> servicio.crear(solicitud())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-06 (positivo): al editar se excluye el propio partido en la consulta del estadio")
    void rn06_alEditar_seExcluyeElPropioPartido() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.PROGRAMADO)));

        servicio.actualizar(PARTIDO, solicitud());

        verify(partidoRepository).horariosOcupadosDelEstadio(eq(ESTADIO), eq(PARTIDO), any(), any());
    }

    // ===================================================================== RN-07

    @Test
    @DisplayName("RN-07 (negativo): el árbitro ya dirige un partido a la misma fecha y hora exactas, 409")
    void rn07_arbitroOcupadoALaMismaHora_lanza409() {
        when(partidoRepository.horariosOcupadosDelArbitro(eq(ARBITRO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA));

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("RN-07");
                    assertThat(ex.getMessage()).contains("Wilmar Roldán");
                });

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("RN-07 (positivo): el árbitro dirige otro partido, pero a otra hora, y con la ventana en cero no choca")
    void rn07_arbitroConOtraHora_seProgramaElPartido() {
        when(partidoRepository.horariosOcupadosDelArbitro(eq(ARBITRO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA.minus(Duration.ofMinutes(90))));

        assertThatCode(() -> servicio.crear(solicitud())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RN-07 (positivo): al editar se excluye el propio partido en la consulta del árbitro")
    void rn07_alEditar_seExcluyeElPropioPartido() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.PROGRAMADO)));

        servicio.actualizar(PARTIDO, solicitud());

        verify(partidoRepository).horariosOcupadosDelArbitro(eq(ARBITRO), eq(PARTIDO), any(), any());
    }

    // ============================================== Ventana de solape de RN-06 y RN-07

    @Test
    @DisplayName("VENTANA_SOLAPE vale cero por defecto: fecha y hora exactas, literal del documento")
    void ventanaSolape_porDefectoEsCero() {
        assertThat(PartidoServiceImpl.VENTANA_SOLAPE).isEqualTo(Duration.ZERO);
    }

    @ParameterizedTest(name = "ventana {0} min, otro partido a {1} min: choca = {2}")
    @CsvSource({
            // ventana, desfase del otro partido (minutos), ¿hay choque?
            "0,   0,    true",
            "0,   30,   false",
            "0,   -30,  false",
            "120, 0,    true",
            "120, 30,   true",
            "120, -119, true",
            "120, 120,  false",
            "120, -120, false",
            "120, 121,  false"
    })
    @DisplayName("RN-06: cambiar la ventana cambia el resultado; solo se toca la constante, no la comparación")
    void ventanaSolape_cambiarlaCambiaElResultadoDeRn06(long ventanaMinutos, long desfaseMinutos, boolean choca) {
        PartidoServiceImpl conVentana = servicioConVentana(Duration.ofMinutes(ventanaMinutos));
        when(partidoRepository.horariosOcupadosDelEstadio(eq(ESTADIO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA.plus(Duration.ofMinutes(desfaseMinutos))));

        if (choca) {
            assertThatThrownBy(() -> conVentana.crear(solicitud()))
                    .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-06"));
        } else {
            assertThatCode(() -> conVentana.crear(solicitud())).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest(name = "ventana {0} min, otro partido a {1} min: choca = {2}")
    @CsvSource({
            "0,   0,    true",
            "0,   30,   false",
            "120, 30,   true",
            "120, -119, true",
            "120, 120,  false"
    })
    @DisplayName("RN-07: la misma ventana rige el choque de árbitro")
    void ventanaSolape_cambiarlaCambiaElResultadoDeRn07(long ventanaMinutos, long desfaseMinutos, boolean choca) {
        PartidoServiceImpl conVentana = servicioConVentana(Duration.ofMinutes(ventanaMinutos));
        when(partidoRepository.horariosOcupadosDelArbitro(eq(ARBITRO), eq(SIN_EXCLUSION), any(), any()))
                .thenReturn(List.of(HORA.plus(Duration.ofMinutes(desfaseMinutos))));

        if (choca) {
            assertThatThrownBy(() -> conVentana.crear(solicitud()))
                    .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-07"));
        } else {
            assertThatCode(() -> conVentana.crear(solicitud())).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("El rango que se pide a la base de datos sale de la misma ventana: [hora - ventana, hora + ventana]")
    void ventanaSolape_elRangoDeBusquedaSaleDeLaVentana() {
        servicioConVentana(Duration.ZERO).crear(solicitud());
        verify(partidoRepository).horariosOcupadosDelEstadio(ESTADIO, SIN_EXCLUSION, HORA, HORA);

        servicioConVentana(Duration.ofHours(2)).crear(solicitud());
        verify(partidoRepository).horariosOcupadosDelEstadio(ESTADIO, SIN_EXCLUSION,
                HORA.minus(Duration.ofHours(2)), HORA.plus(Duration.ofHours(2)));
        verify(partidoRepository).horariosOcupadosDelArbitro(ARBITRO, SIN_EXCLUSION,
                HORA.minus(Duration.ofHours(2)), HORA.plus(Duration.ofHours(2)));
    }

    // ============================================ Última defensa: índices únicos de la base de datos

    @Test
    @DisplayName("RN-06: si dos solicitudes simultáneas llegan al índice único del estadio, se responde 409 RN-06")
    void rn06_indiceUnicoDeEstadio_seTraduceAConflictoRn06() {
        when(partidoRepository.saveAndFlush(any(Partido.class))).thenThrow(violacion("uq_partido_estadio_fecha"));

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-06"));
    }

    @Test
    @DisplayName("RN-07: si dos solicitudes simultáneas llegan al índice único del árbitro, se responde 409 RN-07")
    void rn07_indiceUnicoDeArbitro_seTraduceAConflictoRn07() {
        when(partidoRepository.saveAndFlush(any(Partido.class))).thenThrow(violacion("uq_partido_arbitro_fecha"));

        assertThatThrownBy(() -> servicio.crear(solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-07"));
    }

    @Test
    @DisplayName("Otra violación de integridad no se disfraza de RN-06 ni RN-07: se propaga tal cual")
    void otraViolacionDeIntegridad_sePropaga() {
        DataIntegrityViolationException original = violacion("fk_partido_liga");
        when(partidoRepository.saveAndFlush(any(Partido.class))).thenThrow(original);

        assertThatThrownBy(() -> servicio.crear(solicitud())).isSameAs(original);
    }

    // ===================================================================== Actualizar

    @Test
    @DisplayName("Actualizar un partido PROGRAMADO: cambia jornada, fecha y relaciones; no toca marcador ni estado")
    void actualizar_partidoProgramado_seReprograma() {
        Partido existente = partidoExistente(EstadoPartido.PROGRAMADO);
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(existente));
        Instant nuevaHora = HORA.plus(Duration.ofDays(7));
        PartidoRequest cambio = new PartidoRequest(LIGA, 4, nuevaHora, VISITANTE, LOCAL, ESTADIO, ARBITRO);

        PartidoResponse respuesta = servicio.actualizar(PARTIDO, cambio);

        assertThat(existente.getJornada()).isEqualTo(4);
        assertThat(existente.getFechaHora()).isEqualTo(nuevaHora);
        assertThat(existente.getEquipoLocal().getId()).isEqualTo(VISITANTE);
        assertThat(existente.getEquipoVisitante().getId()).isEqualTo(LOCAL);
        assertThat(existente.getId()).isEqualTo(PARTIDO);
        assertThat(existente.getEstado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(respuesta.jornada()).isEqualTo(4);
        verify(partidoRepository).saveAndFlush(existente);
    }

    @Test
    @DisplayName("Actualizar un partido que no existe: 404")
    void actualizar_partidoInexistente_lanza404() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(PARTIDO, solicitud()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ===================================================================== RN-12

    @Test
    @DisplayName("RN-12 (negativo): un partido FINALIZADO no se puede modificar, 409 RN-12")
    void rn12_actualizarPartidoFinalizado_lanza409() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.FINALIZADO)));

        assertThatThrownBy(() -> servicio.actualizar(PARTIDO, solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-12"));

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
        verifyNoInteractions(ligaRepository, equipoRepository, estadioRepository, arbitroRepository);
    }

    @Test
    @DisplayName("RN-12 (negativo): un partido FINALIZADO no se puede eliminar, 409 RN-12")
    void rn12_eliminarPartidoFinalizado_lanza409() {
        Partido finalizado = partidoExistente(EstadoPartido.FINALIZADO);
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(finalizado));

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("RN-12"));

        verify(partidoRepository, never()).delete(any(Partido.class));
    }

    @Test
    @DisplayName("PARTIDO_EN_JUEGO (supuesto): un partido EN_JUEGO tampoco se modifica, 409 PARTIDO_EN_JUEGO")
    void actualizarPartidoEnJuego_lanza409() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.EN_JUEGO)));

        assertThatThrownBy(() -> servicio.actualizar(PARTIDO, solicitud()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("PARTIDO_EN_JUEGO"));

        verify(partidoRepository, never()).saveAndFlush(any(Partido.class));
    }

    @Test
    @DisplayName("PARTIDO_EN_JUEGO (supuesto): un partido EN_JUEGO tampoco se elimina, 409 PARTIDO_EN_JUEGO")
    void eliminarPartidoEnJuego_lanza409() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.EN_JUEGO)));

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("PARTIDO_EN_JUEGO"));

        verify(partidoRepository, never()).delete(any(Partido.class));
    }

    @Test
    @DisplayName("RN-12 (positivo): un partido PROGRAMADO sí se elimina")
    void rn12_eliminarPartidoProgramado_loElimina() {
        Partido programado = partidoExistente(EstadoPartido.PROGRAMADO);
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(programado));

        servicio.eliminar(PARTIDO);

        verify(partidoRepository).delete(programado);
    }

    @Test
    @DisplayName("Eliminar un partido que no existe: 404")
    void eliminar_partidoInexistente_lanza404() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);

        verify(partidoRepository, never()).delete(any(Partido.class));
    }

    // ===================================================================== Iniciar

    @Test
    @DisplayName("Iniciar un partido PROGRAMADO: pasa a EN_JUEGO")
    void iniciar_partidoProgramado_pasaAEnJuego() {
        Partido programado = partidoExistente(EstadoPartido.PROGRAMADO);
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(programado));

        PartidoResponse respuesta = servicio.iniciar(PARTIDO);

        assertThat(programado.getEstado()).isEqualTo(EstadoPartido.EN_JUEGO);
        assertThat(respuesta.estado()).isEqualTo(EstadoPartido.EN_JUEGO);
        verify(partidoRepository).save(programado);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPartido.class, names = {"EN_JUEGO", "FINALIZADO"})
    @DisplayName("Iniciar un partido que no está PROGRAMADO: 409 PARTIDO_ESTADO_INVALIDO y el estado no cambia")
    void iniciar_partidoNoProgramado_lanza409(EstadoPartido estado) {
        Partido partido = partidoExistente(estado);
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partido));

        assertThatThrownBy(() -> servicio.iniciar(PARTIDO))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("PARTIDO_ESTADO_INVALIDO"));

        assertThat(partido.getEstado()).isEqualTo(estado);
        verify(partidoRepository, never()).save(any(Partido.class));
    }

    @Test
    @DisplayName("Iniciar un partido que no existe: 404")
    void iniciar_partidoInexistente_lanza404() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ============================================================== Obtener y calendario

    @Test
    @DisplayName("Obtener un partido existente devuelve su respuesta resumida")
    void obtener_partidoExistente_devuelveLaRespuesta() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.of(partidoExistente(EstadoPartido.PROGRAMADO)));

        PartidoResponse respuesta = servicio.obtener(PARTIDO);

        assertThat(respuesta.id()).isEqualTo(PARTIDO);
        assertThat(respuesta.liga().nombre()).isEqualTo("Liga Betplay");
    }

    @Test
    @DisplayName("Obtener un partido que no existe: 404")
    void obtener_partidoInexistente_lanza404() {
        when(partidoRepository.findById(PARTIDO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Calendario: devuelve la página con la forma PaginaResponse y mapea cada partido")
    void listar_devuelveLaPaginaMapeada() {
        Pageable pedido = PageRequest.of(1, 10);
        Partido partido = partidoExistente(EstadoPartido.PROGRAMADO);
        when(partidoRepository.findAll(ArgumentMatchers.<Specification<Partido>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(partido), PageRequest.of(1, 10, PartidoServiceImpl.ORDEN_POR_DEFECTO), 11));

        PaginaResponse<PartidoResponse> pagina = servicio.listar(LIGA, 3, LOCAL, ESTADIO, pedido);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).id()).isEqualTo(PARTIDO);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamanio()).isEqualTo(10);
        assertThat(pagina.totalElementos()).isEqualTo(11);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("Calendario: sin orden pedido usa jornada, fecha e id; el tamaño máximo es 100")
    void listar_aplicaOrdenPorDefectoYTamanioMaximo() {
        when(partidoRepository.findAll(ArgumentMatchers.<Specification<Partido>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.listar(null, null, null, null, PageRequest.of(0, 100_000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository).findAll(ArgumentMatchers.<Specification<Partido>>any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort()).isEqualTo(PartidoServiceImpl.ORDEN_POR_DEFECTO);
    }

    @Test
    @DisplayName("Calendario: acepta ordenar por una propiedad permitida y descarta una desconocida (evita un 500)")
    void listar_filtraElOrdenPedido() {
        when(partidoRepository.findAll(ArgumentMatchers.<Specification<Partido>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        Sort permitido = Sort.by(Sort.Direction.DESC, "fechaHora");

        servicio.listar(null, null, null, null, PageRequest.of(0, 20, permitido));
        servicio.listar(null, null, null, null, PageRequest.of(0, 20, Sort.by("passwordHash")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository, org.mockito.Mockito.times(2))
                .findAll(ArgumentMatchers.<Specification<Partido>>any(), captor.capture());
        assertThat(captor.getAllValues().get(0).getSort()).isEqualTo(permitido);
        assertThat(captor.getAllValues().get(1).getSort()).isEqualTo(PartidoServiceImpl.ORDEN_POR_DEFECTO);
    }

    // ===================================================================== Apoyo

    private PartidoServiceImpl servicioConVentana(Duration ventana) {
        return new PartidoServiceImpl(partidoRepository, new PartidoMapperImpl(), ligaRepository, equipoRepository,
                estadioRepository, arbitroRepository, ventana);
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("no se pudo ejecutar la sentencia",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + restriccion + "\""));
    }

    private static PartidoRequest solicitud() {
        return new PartidoRequest(LIGA, 3, HORA, LOCAL, VISITANTE, ESTADIO, ARBITRO);
    }

    private static Partido partidoExistente(EstadoPartido estado) {
        Partido partido = new Partido();
        partido.setId(PARTIDO);
        partido.setLiga(liga());
        partido.setJornada(3);
        partido.setFechaHora(HORA);
        partido.setEquipoLocal(equipo(LOCAL, "Cali FC"));
        partido.setEquipoVisitante(equipo(VISITANTE, "Medellín FC"));
        partido.setEstadio(estadio());
        partido.setArbitro(arbitro());
        partido.setEstado(estado);
        return partido;
    }

    private static Liga liga() {
        Liga liga = new Liga();
        liga.setId(LIGA);
        liga.setNombre("Liga Betplay");
        return liga;
    }

    private static Equipo equipo(long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

    private static Estadio estadio() {
        Estadio estadio = new Estadio();
        estadio.setId(ESTADIO);
        estadio.setNombre("Pascual Guerrero");
        return estadio;
    }

    private static Arbitro arbitro() {
        Arbitro arbitro = new Arbitro();
        arbitro.setId(ARBITRO);
        arbitro.setNombre("Wilmar Roldán");
        return arbitro;
    }
}

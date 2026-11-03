package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.PosicionJugador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.JugadorMapperImpl;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de {@link JugadorServiceImpl} con Mockito (sin Spring ni base de datos). Se usa el mapper real y un
 * reloj fijo (2 de noviembre de 2026) para que ninguna prueba dependa de la fecha en que se ejecuta.
 */
@ExtendWith(MockitoExtension.class)
class JugadorServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);

    private static final long ID_JUGADOR = 100L;
    private static final long ID_EQUIPO_ORIGEN = 10L;
    private static final long ID_EQUIPO_DESTINO = 20L;

    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private JugadorServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new JugadorServiceImpl(jugadorRepository, equipoRepository, new JugadorMapperImpl(),
                verificadoresEliminacion, RELOJ);
    }

    // ============================================================== crear

    @Test
    @DisplayName("Crear: con datos válidos guarda al jugador en su equipo y devuelve el resumen del equipo")
    void crear_datosValidos_guardaAlJugador() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali")));
        guardarAsignandoId();

        JugadorResponse respuesta = servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, HOY.minusYears(25)));

        assertThat(respuesta.id()).isEqualTo(ID_JUGADOR);
        assertThat(respuesta.nombre()).isEqualTo("Juan Perez");
        assertThat(respuesta.dorsal()).isEqualTo(9);
        assertThat(respuesta.posicion()).isEqualTo(PosicionJugador.DELANTERO);
        assertThat(respuesta.equipo().id()).isEqualTo(ID_EQUIPO_ORIGEN);
        assertThat(respuesta.equipo().nombre()).isEqualTo("Deportivo Cali");
    }

    @Test
    @DisplayName("Normalización: el nombre se guarda sin espacios sobrantes")
    void crear_normalizaElNombre() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali")));
        guardarAsignandoId();
        JugadorRequest peticion = new JugadorRequest("   Juan    Carlos   Perez  ", HOY.minusYears(25),
                PosicionJugador.DEFENSA, 4, ID_EQUIPO_ORIGEN);

        assertThat(servicio.crear(peticion).nombre()).isEqualTo("Juan Carlos Perez");
    }

    @Test
    @DisplayName("Dorsal único por equipo positivo: un dorsal libre en el equipo se acepta")
    void dorsalUnico_crear_dorsalLibre_seAcepta() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali")));
        when(jugadorRepository.existsByEquipoIdAndDorsal(ID_EQUIPO_ORIGEN, 9)).thenReturn(false);
        guardarAsignandoId();

        assertThat(servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, HOY.minusYears(25))).dorsal()).isEqualTo(9);
    }

    @Test
    @DisplayName("Dorsal único por equipo negativo: un dorsal ocupado en el equipo lanza 409 DORSAL_DUPLICADO")
    void dorsalUnico_crear_dorsalOcupado_lanzaConflicto() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali")));
        when(jugadorRepository.existsByEquipoIdAndDorsal(ID_EQUIPO_ORIGEN, 9)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, HOY.minusYears(25))))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("DORSAL_DUPLICADO"));
        verify(jugadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fecha de nacimiento positivo: ayer es una fecha en el pasado")
    void fechaNacimiento_crear_ayer_esValida() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali")));
        guardarAsignandoId();

        assertThat(servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, HOY.minusDays(1))).fechaNacimiento())
                .isEqualTo(HOY.minusDays(1));
    }

    @Test
    @DisplayName("Fecha de nacimiento negativo: hoy o una fecha futura lanza 422 FECHA_NACIMIENTO_FUTURA")
    void fechaNacimiento_crear_hoyOFutura_lanza422() {
        for (LocalDate invalida : List.of(HOY, HOY.plusDays(1), HOY.plusYears(5))) {
            assertThatThrownBy(() -> servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, invalida)))
                    .as("fecha %s", invalida)
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            ex -> assertThat(ex.getCodigo()).isEqualTo("FECHA_NACIMIENTO_FUTURA"));
        }
        verifyNoInteractions(equipoRepository);
        verify(jugadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: un equipo inexistente lanza 404")
    void crear_equipoInexistente_lanza404() {
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticion(9, ID_EQUIPO_ORIGEN, HOY.minusYears(25))))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Equipo");
        verify(jugadorRepository, never()).save(any());
    }

    // ========================================================== actualizar

    @Test
    @DisplayName("RN-02 positivo: cambiar equipoId mueve al MISMO jugador al equipo destino (no se duplica)")
    void rn02_actualizar_cambiaDeEquipo_mueveAlMismoJugador() {
        Equipo origen = equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali");
        Equipo destino = equipo(ID_EQUIPO_DESTINO, "America de Cali");
        Jugador existente = jugador(9, origen);
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));
        when(equipoRepository.findById(ID_EQUIPO_DESTINO)).thenReturn(Optional.of(destino));
        when(jugadorRepository.existsByEquipoIdAndDorsalAndIdNot(ID_EQUIPO_DESTINO, 9, ID_JUGADOR)).thenReturn(false);

        JugadorResponse respuesta = servicio.actualizar(ID_JUGADOR,
                peticion(9, ID_EQUIPO_DESTINO, HOY.minusYears(25)));

        assertThat(existente.getEquipo()).isSameAs(destino);
        assertThat(respuesta.id()).isEqualTo(ID_JUGADOR);
        assertThat(respuesta.equipo().id()).isEqualTo(ID_EQUIPO_DESTINO);
        // Se modificó la fila existente: no se insertó ningún jugador nuevo.
        verify(jugadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN-02 negativo: si el dorsal ya está ocupado en el equipo destino, lanza 409 DORSAL_DUPLICADO y no lo mueve")
    void rn02_actualizar_dorsalOcupadoEnElDestino_lanzaConflictoYNoMueve() {
        Equipo origen = equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali");
        Equipo destino = equipo(ID_EQUIPO_DESTINO, "America de Cali");
        Jugador existente = jugador(9, origen);
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));
        when(equipoRepository.findById(ID_EQUIPO_DESTINO)).thenReturn(Optional.of(destino));
        when(jugadorRepository.existsByEquipoIdAndDorsalAndIdNot(ID_EQUIPO_DESTINO, 9, ID_JUGADOR)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID_JUGADOR, peticion(9, ID_EQUIPO_DESTINO, HOY.minusYears(25))))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("DORSAL_DUPLICADO"));
        assertThat(existente.getEquipo()).isSameAs(origen);
    }

    @Test
    @DisplayName("RN-02 negativo: mover a un equipo inexistente lanza 404 y el jugador sigue en su equipo")
    void rn02_actualizar_equipoDestinoInexistente_lanza404() {
        Equipo origen = equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali");
        Jugador existente = jugador(9, origen);
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));
        when(equipoRepository.findById(ID_EQUIPO_DESTINO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID_JUGADOR, peticion(9, ID_EQUIPO_DESTINO, HOY.minusYears(25))))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(existente.getEquipo()).isSameAs(origen);
    }

    @Test
    @DisplayName("Dorsal único por equipo positivo: al actualizar se excluye al propio jugador (conservar su dorsal es válido)")
    void dorsalUnico_actualizar_conservaSuDorsal_excluyeAlPropioJugador() {
        Equipo origen = equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali");
        Jugador existente = jugador(9, origen);
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(origen));
        when(jugadorRepository.existsByEquipoIdAndDorsalAndIdNot(ID_EQUIPO_ORIGEN, 9, ID_JUGADOR)).thenReturn(false);

        JugadorResponse respuesta = servicio.actualizar(ID_JUGADOR,
                new JugadorRequest("  Juan   Perez Jr ", HOY.minusYears(25), PosicionJugador.MEDIOCAMPISTA, 9,
                        ID_EQUIPO_ORIGEN));

        assertThat(respuesta.nombre()).isEqualTo("Juan Perez Jr");
        assertThat(respuesta.posicion()).isEqualTo(PosicionJugador.MEDIOCAMPISTA);
        verify(jugadorRepository).existsByEquipoIdAndDorsalAndIdNot(ID_EQUIPO_ORIGEN, 9, ID_JUGADOR);
    }

    @Test
    @DisplayName("Dorsal único por equipo negativo: al actualizar, el dorsal de otro compañero lanza 409 DORSAL_DUPLICADO")
    void dorsalUnico_actualizar_dorsalDeOtroCompanero_lanzaConflicto() {
        Equipo origen = equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali");
        Jugador existente = jugador(9, origen);
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));
        when(equipoRepository.findById(ID_EQUIPO_ORIGEN)).thenReturn(Optional.of(origen));
        when(jugadorRepository.existsByEquipoIdAndDorsalAndIdNot(ID_EQUIPO_ORIGEN, 10, ID_JUGADOR)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID_JUGADOR, peticion(10, ID_EQUIPO_ORIGEN, HOY.minusYears(25))))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("DORSAL_DUPLICADO"));
        assertThat(existente.getDorsal()).isEqualTo(9);
    }

    @Test
    @DisplayName("Fecha de nacimiento negativo: al actualizar, una fecha futura lanza 422 y no modifica al jugador")
    void fechaNacimiento_actualizar_fechaFutura_lanza422() {
        Jugador existente = jugador(9, equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali"));
        when(jugadorRepository.findById(ID_JUGADOR)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.actualizar(ID_JUGADOR, peticion(9, ID_EQUIPO_ORIGEN, HOY.plusDays(1))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("FECHA_NACIMIENTO_FUTURA"));
        assertThat(existente.getFechaNacimiento()).isEqualTo(HOY.minusYears(25));
    }

    @Test
    @DisplayName("Actualizar: un jugador inexistente lanza 404")
    void actualizar_jugadorInexistente_lanza404() {
        when(jugadorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(999L, peticion(9, ID_EQUIPO_ORIGEN, HOY.minusYears(25))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ============================================================ eliminar

    @Test
    @DisplayName("Eliminar positivo: consulta a los verificadores con (Jugador, id) y borra")
    void eliminar_sinDependientes_consultaAVerificadoresYBorra() {
        when(jugadorRepository.existsById(ID_JUGADOR)).thenReturn(true);

        servicio.eliminar(ID_JUGADOR);

        verify(verificadoresEliminacion).verificar(Jugador.class, ID_JUGADOR);
        verify(jugadorRepository).deleteById(ID_JUGADOR);
    }

    @Test
    @DisplayName("Eliminar negativo: si un verificador (goles o tarjetas) lanza 409, se propaga y no borra")
    void eliminar_conDependientes_sePropagaElConflictoYNoBorra() {
        when(jugadorRepository.existsById(ID_JUGADOR)).thenReturn(true);
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "tiene goles"))
                .when(verificadoresEliminacion).verificar(Jugador.class, ID_JUGADOR);

        assertThatThrownBy(() -> servicio.eliminar(ID_JUGADOR)).isInstanceOf(ConflictoException.class);
        verify(jugadorRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar: un jugador inexistente lanza 404 y no consulta a los verificadores")
    void eliminar_jugadorInexistente_lanza404() {
        when(jugadorRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.eliminar(999L)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(verificadoresEliminacion);
    }

    // ============================================================ lecturas

    @Test
    @DisplayName("Obtener: devuelve al jugador; si no existe, 404")
    void obtener_existente_devuelveAlJugador_inexistente_lanza404() {
        when(jugadorRepository.findById(ID_JUGADOR))
                .thenReturn(Optional.of(jugador(9, equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali"))));
        when(jugadorRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(ID_JUGADOR).equipo().nombre()).isEqualTo("Deportivo Cali");
        assertThatThrownBy(() -> servicio.obtener(999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar sin equipoId: consulta todos y normaliza un orden no permitido y un tamaño excesivo")
    void listar_sinFiltro_normalizaLaPaginacion() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(jugadorRepository.findAll(captor.capture())).thenAnswer(inv ->
                new PageImpl<>(List.of(jugador(9, equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali"))), inv.getArgument(0), 1));

        PaginaResponse<JugadorResponse> pagina = servicio.listar(null, PageRequest.of(0, 5000, Sort.by("equipo.liga")));

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort()).isEqualTo(JugadorServiceImpl.ORDEN_POR_DEFECTO);
        assertThat(pagina.contenido()).hasSize(1);
        verifyNoInteractions(equipoRepository);
    }

    @Test
    @DisplayName("Listar con equipoId: devuelve el plantel de ese equipo")
    void listar_conEquipoId_devuelveElPlantel() {
        when(equipoRepository.existsById(ID_EQUIPO_ORIGEN)).thenReturn(true);
        when(jugadorRepository.findByEquipoId(any(Long.class), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(jugador(9, equipo(ID_EQUIPO_ORIGEN, "Deportivo Cali"))), inv.getArgument(1), 1));

        PaginaResponse<JugadorResponse> pagina = servicio.listar(ID_EQUIPO_ORIGEN, PageRequest.of(0, 20));

        assertThat(pagina.contenido()).extracting(JugadorResponse::dorsal).containsExactly(9);
        verify(jugadorRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Plantel: por defecto se ordena por dorsal y se respeta un orden permitido")
    void plantel_ordenPorDefectoEsElDorsal_yRespetaUnOrdenPermitido() {
        when(equipoRepository.existsById(ID_EQUIPO_ORIGEN)).thenReturn(true);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(jugadorRepository.findByEquipoId(any(Long.class), captor.capture())).thenAnswer(inv ->
                new PageImpl<Jugador>(List.of()));

        servicio.plantel(ID_EQUIPO_ORIGEN, PageRequest.of(0, 20));
        servicio.plantel(ID_EQUIPO_ORIGEN, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "nombre")));

        assertThat(captor.getAllValues().get(0).getSort()).isEqualTo(JugadorServiceImpl.ORDEN_PLANTEL);
        assertThat(captor.getAllValues().get(1).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "nombre"));
    }

    @Test
    @DisplayName("Plantel: un equipo inexistente lanza 404 y no consulta jugadores")
    void plantel_equipoInexistente_lanza404() {
        when(equipoRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.plantel(999L, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(jugadorRepository);
    }

    @Test
    @DisplayName("Listar con equipoId de un equipo inexistente: 404")
    void listar_conEquipoIdInexistente_lanza404() {
        when(equipoRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.listar(999L, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Los órdenes permitidos existen como propiedades de Jugador (un nombre mal escrito rompería el ?sort=)")
    void ordenesPermitidos_sonPropiedadesDeJugador() {
        List<String> campos = java.util.Arrays.stream(Jugador.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();

        assertThat(campos).containsAll(JugadorServiceImpl.ORDENES_PERMITIDOS);
        assertThat(campos).containsAll(JugadorServiceImpl.ORDEN_POR_DEFECTO.stream().map(Sort.Order::getProperty).toList());
        assertThat(campos).containsAll(JugadorServiceImpl.ORDEN_PLANTEL.stream().map(Sort.Order::getProperty).toList());
    }

    // ============================================================== apoyo

    private static JugadorRequest peticion(int dorsal, long equipoId, LocalDate fechaNacimiento) {
        return new JugadorRequest("Juan Perez", fechaNacimiento, PosicionJugador.DELANTERO, dorsal, equipoId);
    }

    private static Equipo equipo(long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

    private static Jugador jugador(int dorsal, Equipo equipo) {
        Jugador jugador = new Jugador();
        jugador.setId(ID_JUGADOR);
        jugador.setNombre("Juan Perez");
        jugador.setFechaNacimiento(HOY.minusYears(25));
        jugador.setPosicion(PosicionJugador.DELANTERO);
        jugador.setDorsal(dorsal);
        jugador.setEquipo(equipo);
        return jugador;
    }

    /** Simula el INSERT: la base de datos asigna el id al guardar. */
    private void guardarAsignandoId() {
        when(jugadorRepository.save(any(Jugador.class))).thenAnswer(inv -> {
            Jugador jugador = inv.getArgument(0);
            jugador.setId(ID_JUGADOR);
            return jugador;
        });
    }
}

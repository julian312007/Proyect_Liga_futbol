package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.EquipoMapperImpl;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.JugadorService;
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
 * Pruebas de {@link EquipoServiceImpl} con Mockito (sin Spring ni base de datos). Se usa el mapper real y un reloj
 * fijo (2 de noviembre de 2026) para que ninguna prueba dependa de la fecha en que se ejecuta.
 */
@ExtendWith(MockitoExtension.class)
class EquipoServiceImplTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
    private static final int ANIO_ACTUAL = 2026;

    private static final long ID_EQUIPO = 10L;
    private static final long ID_LIGA = 1L;
    private static final long ID_ESTADIO = 2L;
    private static final long ID_ENTRENADOR = 3L;

    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private LigaRepository ligaRepository;
    @Mock
    private EstadioRepository estadioRepository;
    @Mock
    private EntrenadorRepository entrenadorRepository;
    @Mock
    private JugadorRepository jugadorRepository;
    @Mock
    private JugadorService jugadorService;
    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private EquipoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new EquipoServiceImpl(equipoRepository, ligaRepository, estadioRepository, entrenadorRepository,
                jugadorRepository, jugadorService, new EquipoMapperImpl(), verificadoresEliminacion, RELOJ);
    }

    // ============================================================== crear

    @Test
    @DisplayName("Crear: con datos válidos guarda el equipo con sus relaciones y devuelve su resumen")
    void crear_datosValidos_guardaYDevuelveElEquipo() {
        catalogosExistentes();
        guardarAsignandoId();

        EquipoResponse respuesta = servicio.crear(peticion("Deportivo Cali", ANIO_ACTUAL));

        assertThat(respuesta.id()).isEqualTo(ID_EQUIPO);
        assertThat(respuesta.nombre()).isEqualTo("Deportivo Cali");
        assertThat(respuesta.ciudad()).isEqualTo("Cali");
        assertThat(respuesta.anioFundacion()).isEqualTo(ANIO_ACTUAL);
        assertThat(respuesta.liga().id()).isEqualTo(ID_LIGA);
        assertThat(respuesta.estadioLocal().id()).isEqualTo(ID_ESTADIO);
        assertThat(respuesta.entrenador().id()).isEqualTo(ID_ENTRENADOR);
        verify(equipoRepository).save(any(Equipo.class));
    }

    @Test
    @DisplayName("Normalización: el nombre y la ciudad se guardan sin espacios sobrantes y se comparan ya normalizados")
    void crear_normalizaNombreYCiudad() {
        catalogosExistentes();
        guardarAsignandoId();
        EquipoRequest peticion = new EquipoRequest("   Deportivo    Cali  ", "  Santiago   de  Cali ", 1912,
                ID_ESTADIO, ID_ENTRENADOR, ID_LIGA);

        EquipoResponse respuesta = servicio.crear(peticion);

        assertThat(respuesta.nombre()).isEqualTo("Deportivo Cali");
        assertThat(respuesta.ciudad()).isEqualTo("Santiago de Cali");
        verify(equipoRepository).existsByLigaIdAndNombreIgnoreCase(ID_LIGA, "Deportivo Cali");
    }

    @Test
    @DisplayName("Equipo único por liga positivo: el mismo nombre en otra liga no es un conflicto")
    void equipoUnico_crear_nombreLibreEnLaLiga_noEsConflicto() {
        catalogosExistentes();
        guardarAsignandoId();
        when(equipoRepository.existsByLigaIdAndNombreIgnoreCase(ID_LIGA, "Deportivo Cali")).thenReturn(false);

        assertThat(servicio.crear(peticion("Deportivo Cali", 1912)).id()).isEqualTo(ID_EQUIPO);
    }

    @Test
    @DisplayName("Equipo único por liga negativo: repetir el nombre en la misma liga lanza 409 EQUIPO_DUPLICADO")
    void equipoUnico_crear_nombreRepetidoEnLaLiga_lanzaConflicto() {
        catalogosExistentes();
        when(equipoRepository.existsByLigaIdAndNombreIgnoreCase(ID_LIGA, "Deportivo Cali")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticion("Deportivo Cali", 1912)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("EQUIPO_DUPLICADO"));
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RN-03 positivo: un entrenador libre se puede asignar a un equipo nuevo")
    void rn03_crear_entrenadorLibre_creaElEquipo() {
        catalogosExistentes();
        guardarAsignandoId();
        when(equipoRepository.existsByEntrenadorId(ID_ENTRENADOR)).thenReturn(false);

        assertThat(servicio.crear(peticion("Deportivo Cali", 1912)).entrenador().id()).isEqualTo(ID_ENTRENADOR);
    }

    @Test
    @DisplayName("RN-03 negativo: un entrenador que ya dirige a otro equipo lanza 409 con código RN-03")
    void rn03_crear_entrenadorDeOtroEquipo_lanzaConflicto() {
        catalogosExistentes();
        when(equipoRepository.existsByEntrenadorId(ID_ENTRENADOR)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticion("Deportivo Cali", 1912)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("RN-03"));
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Año de fundación positivo: el año actual es válido (no es futuro)")
    void anioFundacion_crear_anioActual_esValido() {
        catalogosExistentes();
        guardarAsignandoId();

        assertThat(servicio.crear(peticion("Nuevo FC", ANIO_ACTUAL)).anioFundacion()).isEqualTo(ANIO_ACTUAL);
    }

    @Test
    @DisplayName("Año de fundación negativo: un año posterior al actual lanza 422 ANIO_FUNDACION_FUTURO")
    void anioFundacion_crear_anioFuturo_lanza422() {
        assertThatThrownBy(() -> servicio.crear(peticion("Nuevo FC", ANIO_ACTUAL + 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("ANIO_FUNDACION_FUTURO"));
        verifyNoInteractions(ligaRepository, estadioRepository, entrenadorRepository);
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Relaciones: una liga inexistente lanza 404")
    void crear_ligaInexistente_lanza404() {
        when(ligaRepository.findById(ID_LIGA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticion("Deportivo Cali", 1912)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Liga");
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Relaciones: un estadio inexistente lanza 404")
    void crear_estadioInexistente_lanza404() {
        when(ligaRepository.findById(ID_LIGA)).thenReturn(Optional.of(liga()));
        when(estadioRepository.findById(ID_ESTADIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticion("Deportivo Cali", 1912)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Estadio");
    }

    @Test
    @DisplayName("Relaciones: un entrenador inexistente lanza 404")
    void crear_entrenadorInexistente_lanza404() {
        when(ligaRepository.findById(ID_LIGA)).thenReturn(Optional.of(liga()));
        when(estadioRepository.findById(ID_ESTADIO)).thenReturn(Optional.of(estadio()));
        when(entrenadorRepository.findById(ID_ENTRENADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticion("Deportivo Cali", 1912)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Entrenador");
    }

    // ========================================================== actualizar

    @Test
    @DisplayName("RN-03 positivo: al actualizar se excluye al propio equipo, así que conservar su entrenador no es conflicto")
    void rn03_actualizar_conservaSuPropioEntrenador_noEsConflicto() {
        catalogosExistentes();
        Equipo existente = equipoExistente("Deportivo Cali");
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(existente));
        when(equipoRepository.existsByEntrenadorIdAndIdNot(ID_ENTRENADOR, ID_EQUIPO)).thenReturn(false);

        EquipoResponse respuesta = servicio.actualizar(ID_EQUIPO, peticion("Deportivo Cali", 1912));

        assertThat(respuesta.id()).isEqualTo(ID_EQUIPO);
        verify(equipoRepository).existsByEntrenadorIdAndIdNot(ID_ENTRENADOR, ID_EQUIPO);
    }

    @Test
    @DisplayName("RN-03 negativo: al actualizar, un entrenador que dirige a OTRO equipo lanza 409 con código RN-03")
    void rn03_actualizar_entrenadorDeOtroEquipo_lanzaConflicto() {
        catalogosExistentes();
        Equipo existente = equipoExistente("Deportivo Cali");
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(existente));
        when(equipoRepository.existsByEntrenadorIdAndIdNot(ID_ENTRENADOR, ID_EQUIPO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID_EQUIPO, peticion("Deportivo Cali", 1912)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("RN-03"));
    }

    @Test
    @DisplayName("Equipo único por liga positivo: al actualizar se excluye al propio equipo (conservar su nombre es válido)")
    void equipoUnico_actualizar_conservaSuNombre_excluyeAlPropioEquipo() {
        catalogosExistentes();
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(equipoExistente("Deportivo Cali")));
        when(equipoRepository.existsByLigaIdAndNombreIgnoreCaseAndIdNot(ID_LIGA, "Deportivo Cali", ID_EQUIPO))
                .thenReturn(false);

        assertThat(servicio.actualizar(ID_EQUIPO, peticion("Deportivo Cali", 1912)).nombre())
                .isEqualTo("Deportivo Cali");
    }

    @Test
    @DisplayName("Equipo único por liga negativo: al actualizar, el nombre de otro equipo de la liga lanza 409")
    void equipoUnico_actualizar_nombreDeOtroEquipo_lanzaConflicto() {
        catalogosExistentes();
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(equipoExistente("Deportivo Cali")));
        when(equipoRepository.existsByLigaIdAndNombreIgnoreCaseAndIdNot(ID_LIGA, "America de Cali", ID_EQUIPO))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID_EQUIPO, peticion("America de Cali", 1912)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("EQUIPO_DUPLICADO"));
    }

    @Test
    @DisplayName("Actualizar: reemplaza los campos y las relaciones del mismo equipo")
    void actualizar_datosValidos_modificaElMismoEquipo() {
        Liga otraLiga = liga();
        otraLiga.setId(20L);
        otraLiga.setNombre("Liga Femenina");
        when(ligaRepository.findById(20L)).thenReturn(Optional.of(otraLiga));
        when(estadioRepository.findById(ID_ESTADIO)).thenReturn(Optional.of(estadio()));
        when(entrenadorRepository.findById(ID_ENTRENADOR)).thenReturn(Optional.of(entrenador()));
        Equipo existente = equipoExistente("Deportivo Cali");
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(existente));

        EquipoResponse respuesta = servicio.actualizar(ID_EQUIPO,
                new EquipoRequest("  Cali   FC ", "Palmira", 1999, ID_ESTADIO, ID_ENTRENADOR, 20L));

        assertThat(existente.getNombre()).isEqualTo("Cali FC");
        assertThat(existente.getCiudad()).isEqualTo("Palmira");
        assertThat(existente.getLiga()).isSameAs(otraLiga);
        assertThat(respuesta.id()).isEqualTo(ID_EQUIPO);
        assertThat(respuesta.liga().nombre()).isEqualTo("Liga Femenina");
        verify(equipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Año de fundación negativo: al actualizar, un año futuro lanza 422 y no modifica el equipo")
    void anioFundacion_actualizar_anioFuturo_lanza422() {
        Equipo existente = equipoExistente("Deportivo Cali");
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.actualizar(ID_EQUIPO, peticion("Deportivo Cali", ANIO_ACTUAL + 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("ANIO_FUNDACION_FUTURO"));
        assertThat(existente.getAnioFundacion()).isEqualTo(1912);
    }

    @Test
    @DisplayName("Actualizar: un equipo inexistente lanza 404")
    void actualizar_equipoInexistente_lanza404() {
        when(equipoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, peticion("Deportivo Cali", 1912)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ============================================================ eliminar

    @Test
    @DisplayName("Eliminar positivo: sin jugadores consulta a los verificadores con (Equipo, id) y borra")
    void eliminar_sinJugadores_consultaAVerificadoresYBorra() {
        when(equipoRepository.existsById(ID_EQUIPO)).thenReturn(true);
        when(jugadorRepository.existsByEquipoId(ID_EQUIPO)).thenReturn(false);

        servicio.eliminar(ID_EQUIPO);

        verify(verificadoresEliminacion).verificar(Equipo.class, ID_EQUIPO);
        verify(equipoRepository).deleteById(ID_EQUIPO);
    }

    @Test
    @DisplayName("Eliminar negativo: un equipo con jugadores lanza 409 CONFLICTO_DEPENDENCIAS y no borra")
    void eliminar_conJugadores_lanzaConflicto() {
        when(equipoRepository.existsById(ID_EQUIPO)).thenReturn(true);
        when(jugadorRepository.existsByEquipoId(ID_EQUIPO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(ID_EQUIPO))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
        verify(equipoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar negativo: si un verificador de otra fase (por ejemplo, partidos) lanza 409, se propaga y no borra")
    void eliminar_verificadorDeOtraFaseLanzaConflicto_sePropagaYNoBorra() {
        when(equipoRepository.existsById(ID_EQUIPO)).thenReturn(true);
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "tiene partidos"))
                .when(verificadoresEliminacion).verificar(Equipo.class, ID_EQUIPO);

        assertThatThrownBy(() -> servicio.eliminar(ID_EQUIPO)).isInstanceOf(ConflictoException.class);
        verify(equipoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar: un equipo inexistente lanza 404 y no consulta a nadie más")
    void eliminar_equipoInexistente_lanza404() {
        when(equipoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(jugadorRepository, verificadoresEliminacion);
    }

    // ============================================================ lecturas

    @Test
    @DisplayName("Obtener: devuelve el equipo con sus resúmenes; si no existe, 404")
    void obtener_existente_devuelveElEquipo_inexistente_lanza404() {
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(equipoExistente("Deportivo Cali")));
        when(equipoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(ID_EQUIPO).entrenador().nombre()).isEqualTo("Alexandre Guimaraes");
        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar: un orden no permitido y un tamaño excesivo se normalizan antes de consultar")
    void listar_ordenNoPermitidoYTamanioExcesivo_seNormaliza() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(equipoRepository.findAll(captor.capture())).thenAnswer(inv ->
                new PageImpl<>(List.of(equipoExistente("Deportivo Cali")), inv.getArgument(0), 1));

        PaginaResponse<EquipoResponse> pagina =
                servicio.listar(PageRequest.of(0, 100_000, Sort.by("passwordHash")));

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort()).isEqualTo(EquipoServiceImpl.ORDEN_POR_DEFECTO);
        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Listar: un orden permitido (anioFundacion) se respeta")
    void listar_ordenPermitido_seRespeta() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(equipoRepository.findAll(captor.capture())).thenAnswer(inv -> new PageImpl<Equipo>(List.of()));

        servicio.listar(PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "anioFundacion")));

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "anioFundacion"));
    }

    @Test
    @DisplayName("Cuerpo técnico: devuelve el entrenador del equipo; si el equipo no existe, 404")
    void cuerpoTecnico_devuelveElEntrenador_equipoInexistente_lanza404() {
        when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(equipoExistente("Deportivo Cali")));
        when(equipoRepository.findById(99L)).thenReturn(Optional.empty());

        CuerpoTecnicoResponse cuerpo = servicio.cuerpoTecnico(ID_EQUIPO);

        assertThat(cuerpo.equipoId()).isEqualTo(ID_EQUIPO);
        assertThat(cuerpo.equipo()).isEqualTo("Deportivo Cali");
        assertThat(cuerpo.entrenadorId()).isEqualTo(ID_ENTRENADOR);
        assertThat(cuerpo.entrenador()).isEqualTo("Alexandre Guimaraes");
        assertThat(cuerpo.nacionalidad()).isEqualTo("Brasil");
        assertThatThrownBy(() -> servicio.cuerpoTecnico(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Plantel: delega en JugadorService, que comprueba que el equipo exista")
    void plantel_delegaEnElServicioDeJugadores() {
        Pageable pageable = PageRequest.of(0, 10);
        PaginaResponse<JugadorResponse> esperada = new PaginaResponse<>(List.of(), 0, 10, 0, 0);
        when(jugadorService.plantel(ID_EQUIPO, pageable)).thenReturn(esperada);

        assertThat(servicio.plantel(ID_EQUIPO, pageable)).isSameAs(esperada);
    }

    @Test
    @DisplayName("Los órdenes permitidos existen como propiedades de Equipo (un nombre mal escrito rompería el ?sort=)")
    void ordenesPermitidos_sonPropiedadesDeEquipo() {
        List<String> campos = java.util.Arrays.stream(Equipo.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();

        assertThat(campos).containsAll(EquipoServiceImpl.ORDENES_PERMITIDOS);
        assertThat(campos).containsAll(EquipoServiceImpl.ORDEN_POR_DEFECTO.stream().map(Sort.Order::getProperty).toList());
    }

    // ============================================================== apoyo

    private static EquipoRequest peticion(String nombre, Integer anio) {
        return new EquipoRequest(nombre, "Cali", anio, ID_ESTADIO, ID_ENTRENADOR, ID_LIGA);
    }

    private static Liga liga() {
        Liga liga = new Liga();
        liga.setId(ID_LIGA);
        liga.setNombre("Liga BetPlay");
        return liga;
    }

    private static Estadio estadio() {
        Estadio estadio = new Estadio();
        estadio.setId(ID_ESTADIO);
        estadio.setNombre("Estadio Pascual Guerrero");
        return estadio;
    }

    private static Entrenador entrenador() {
        Entrenador entrenador = new Entrenador();
        entrenador.setId(ID_ENTRENADOR);
        entrenador.setNombre("Alexandre Guimaraes");
        entrenador.setNacionalidad("Brasil");
        return entrenador;
    }

    private void catalogosExistentes() {
        when(ligaRepository.findById(ID_LIGA)).thenReturn(Optional.of(liga()));
        when(estadioRepository.findById(ID_ESTADIO)).thenReturn(Optional.of(estadio()));
        when(entrenadorRepository.findById(ID_ENTRENADOR)).thenReturn(Optional.of(entrenador()));
    }

    private static Equipo equipoExistente(String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(ID_EQUIPO);
        equipo.setNombre(nombre);
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1912);
        equipo.setLiga(liga());
        equipo.setEstadioLocal(estadio());
        equipo.setEntrenador(entrenador());
        return equipo;
    }

    /** Simula el INSERT: la base de datos asigna el id al guardar. */
    private void guardarAsignandoId() {
        when(equipoRepository.save(any(Equipo.class))).thenAnswer(inv -> {
            Equipo equipo = inv.getArgument(0);
            equipo.setId(ID_EQUIPO);
            return equipo;
        });
    }
}

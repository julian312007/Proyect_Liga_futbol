package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.config.NombresCache;
import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.exception.ReglaNegocioException;
import co.edu.uniajc.liga.mapper.LigaMapper;
import co.edu.uniajc.liga.mapper.LigaMapperImpl;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas del servicio de ligas: fechas coherentes (422 {@code LIGA_FECHAS_INVALIDAS}), único (nombre,
 * temporada) (409 {@code LIGA_DUPLICADA}, al crear y al actualizar excluyendo la propia liga) y consulta a los
 * verificadores antes de borrar. Mockito puro; el {@code @CacheEvict} se comprueba aquí por reflexión y su
 * efecto real en {@code LigaServiceImplCacheTest}.
 */
@ExtendWith(MockitoExtension.class)
class LigaServiceImplTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 2, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 6, 30);

    @Mock
    private LigaRepository ligaRepository;

    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private final LigaMapper ligaMapper = new LigaMapperImpl();

    private LigaServiceImpl servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new LigaServiceImpl(ligaRepository, ligaMapper, verificadoresEliminacion);
    }

    private static Liga liga(Long id, String nombre, String temporada) {
        Liga liga = new Liga();
        liga.setId(id);
        liga.setNombre(nombre);
        liga.setTemporada(temporada);
        liga.setFechaInicio(INICIO);
        liga.setFechaFin(FIN);
        return liga;
    }

    // ============================================================ Fechas: LIGA_FECHAS_INVALIDAS

    @Test
    @DisplayName("Fechas positivo: fechaFin un día después de fechaInicio (el límite) es válido")
    void fechas_finUnDiaDespuesDelInicio_esValido() {
        when(ligaRepository.existsByNombreAndTemporada("Liga BetPlay", "2026-I")).thenReturn(false);
        when(ligaRepository.save(any(Liga.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        LigaResponse respuesta = servicio.crear(new LigaRequest("Liga BetPlay", "2026-I", INICIO, INICIO.plusDays(1)));

        assertThat(respuesta.fechaFin()).isEqualTo(INICIO.plusDays(1));
    }

    @Test
    @DisplayName("Fechas negativo: fechaFin igual a fechaInicio da 422 LIGA_FECHAS_INVALIDAS y no toca la base de datos")
    void fechas_finIgualAlInicio_lanza422() {
        assertThatThrownBy(() -> servicio.crear(new LigaRequest("Liga BetPlay", "2026-I", INICIO, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("LIGA_FECHAS_INVALIDAS"));

        verifyNoInteractions(ligaRepository);
    }

    @Test
    @DisplayName("Fechas negativo: fechaFin anterior a fechaInicio da 422 LIGA_FECHAS_INVALIDAS")
    void fechas_finAntesDelInicio_lanza422() {
        assertThatThrownBy(() -> servicio.crear(new LigaRequest("Liga BetPlay", "2026-I", FIN, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("LIGA_FECHAS_INVALIDAS");
                    assertThat(ex.getMessage()).contains("2026-02-01").contains("2026-06-30");
                });

        verify(ligaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fechas negativo: al actualizar también se exige fechaFin posterior a fechaInicio")
    void fechas_actualizar_finAntesDelInicio_lanza422() {
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(liga(1L, "Liga BetPlay", "2026-I")));

        assertThatThrownBy(() -> servicio.actualizar(1L, new LigaRequest("Liga BetPlay", "2026-I", FIN, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("LIGA_FECHAS_INVALIDAS"));

        verify(ligaRepository, never()).saveAndFlush(any());
    }

    // ==================================================== Unicidad: LIGA_DUPLICADA (crear)

    @Test
    @DisplayName("Único (nombre, temporada) positivo: crea la liga con el nombre y la temporada normalizados")
    void unicidad_crear_libre_guardaNormalizado() {
        when(ligaRepository.existsByNombreAndTemporada("Liga BetPlay", "2026-I")).thenReturn(false);
        when(ligaRepository.save(any(Liga.class))).thenAnswer(invocacion -> {
            Liga guardada = invocacion.getArgument(0);
            guardada.setId(1L);
            return guardada;
        });

        LigaResponse respuesta = servicio.crear(new LigaRequest("  Liga   BetPlay ", " 2026-I ", INICIO, FIN));

        assertThat(respuesta).isEqualTo(new LigaResponse(1L, "Liga BetPlay", "2026-I", INICIO, FIN));
    }

    @Test
    @DisplayName("Único (nombre, temporada) negativo: la misma liga en la misma temporada da 409 LIGA_DUPLICADA")
    void unicidad_crear_duplicada_lanza409() {
        when(ligaRepository.existsByNombreAndTemporada("Liga BetPlay", "2026-I")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new LigaRequest("Liga BetPlay", "2026-I", INICIO, FIN)))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("LIGA_DUPLICADA");
                    assertThat(ex.getMessage()).contains("Liga BetPlay").contains("2026-I");
                });

        verify(ligaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Único (nombre, temporada) positivo: el mismo nombre en otra temporada es otra liga")
    void unicidad_crear_mismoNombreOtraTemporada_esValido() {
        when(ligaRepository.existsByNombreAndTemporada("Liga BetPlay", "2026-II")).thenReturn(false);
        when(ligaRepository.save(any(Liga.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        LigaResponse respuesta = servicio.crear(new LigaRequest("Liga BetPlay", "2026-II", INICIO, FIN));

        assertThat(respuesta.temporada()).isEqualTo("2026-II");
    }

    // ================================================= Unicidad: LIGA_DUPLICADA (actualizar)

    @Test
    @DisplayName("Único (nombre, temporada) positivo: actualizar sin cambiar nombre ni temporada excluye a la propia liga")
    void unicidad_actualizar_propiaLiga_noEsDuplicada() {
        Liga existente = liga(1L, "Liga BetPlay", "2026-I");
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ligaRepository.existsByNombreAndTemporadaAndIdNot("Liga BetPlay", "2026-I", 1L)).thenReturn(false);
        when(ligaRepository.saveAndFlush(existente)).thenReturn(existente);

        LigaResponse respuesta = servicio.actualizar(1L,
                new LigaRequest("Liga BetPlay", "2026-I", INICIO, LocalDate.of(2026, 7, 15)));

        assertThat(respuesta.fechaFin()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(respuesta.id()).isEqualTo(1L);
        verify(ligaRepository, never()).existsByNombreAndTemporada(any(), any());
    }

    @Test
    @DisplayName("Único (nombre, temporada) negativo: actualizar al nombre y temporada de otra liga da 409 LIGA_DUPLICADA")
    void unicidad_actualizar_deOtraLiga_lanza409() {
        Liga existente = liga(1L, "Liga BetPlay", "2026-I");
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ligaRepository.existsByNombreAndTemporadaAndIdNot("Copa Colombia", "2026-I", 1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, new LigaRequest("Copa Colombia", "2026-I", INICIO, FIN)))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("LIGA_DUPLICADA"));

        verify(ligaRepository, never()).saveAndFlush(any());
        assertThat(existente.getNombre()).isEqualTo("Liga BetPlay");
    }

    @Test
    @DisplayName("Actualizar una liga inexistente: 404")
    void actualizar_noExiste_lanza404() {
        when(ligaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, new LigaRequest("X", "2026-I", INICIO, FIN)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // =============================================================== obtener y listar

    @Test
    @DisplayName("Obtener: positivo (DTO) y negativo (404)")
    void obtener() {
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(liga(1L, "Liga BetPlay", "2026-I")));
        when(ligaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(1L)).isEqualTo(new LigaResponse(1L, "Liga BetPlay", "2026-I", INICIO, FIN));
        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar devuelve la página como DTO y normaliza la paginación (máx. 100, orden desconocido descartado)")
    void listar_normalizaLaPaginacion() {
        when(ligaRepository.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(liga(1L, "Liga BetPlay", "2026-I")), PageRequest.of(0, 20), 1));

        PaginaResponse<LigaResponse> pagina = servicio.listar(PageRequest.of(0, 9_999, Sort.by("hack")));

        assertThat(pagina.contenido()).hasSize(1);
        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(ligaRepository).findAll(pedido.capture());
        assertThat(pedido.getValue().getPageSize()).isEqualTo(100);
        assertThat(pedido.getValue().getSort()).isEqualTo(LigaServiceImpl.ORDEN_POR_DEFECTO);
    }

    // ================================================================= eliminar y caché

    @Test
    @DisplayName("Eliminar positivo: sin dependientes, consulta a los verificadores de Liga y elimina")
    void eliminar_sinDependientes_elimina() {
        Liga existente = liga(1L, "Liga BetPlay", "2026-I");
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(existente));

        servicio.eliminar(1L);

        InOrder orden = inOrder(verificadoresEliminacion, ligaRepository);
        orden.verify(verificadoresEliminacion).verificar(Liga.class, 1L);
        orden.verify(ligaRepository).delete(existente);
    }

    @Test
    @DisplayName("Eliminar negativo: si la liga tiene equipos o partidos, el 409 del verificador se propaga y no se elimina")
    void eliminar_conDependientes_lanza409() {
        when(ligaRepository.findById(1L)).thenReturn(Optional.of(liga(1L, "Liga BetPlay", "2026-I")));
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "La liga tiene equipos"))
                .when(verificadoresEliminacion).verificar(Liga.class, 1L);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));

        verify(ligaRepository, never()).delete(any(Liga.class));
    }

    @Test
    @DisplayName("Eliminar una liga inexistente: 404 sin consultar a los verificadores")
    void eliminar_noExiste_lanza404() {
        when(ligaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(verificadoresEliminacion);
    }

    @Test
    @DisplayName("LigaServiceImpl.eliminar lleva @CacheEvict sobre la tabla de posiciones y los goleadores, con el id como clave")
    void eliminar_llevaCacheEvictDeLasEstadisticas() throws NoSuchMethodException {
        CacheEvict anotacion = LigaServiceImpl.class.getMethod("eliminar", Long.class).getAnnotation(CacheEvict.class);

        assertThat(anotacion).isNotNull();
        assertThat(anotacion.cacheNames())
                .containsExactlyInAnyOrder(NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES);
        assertThat(anotacion.key()).isEqualTo("#id");
        assertThat(anotacion.beforeInvocation()).isFalse();
    }
}

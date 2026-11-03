package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.EstadioMapper;
import co.edu.uniajc.liga.mapper.EstadioMapperImpl;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
 * Reglas del servicio de estadios (D-4): EST-01 (nombre normalizado y único por ciudad, al crear y al
 * actualizar excluyendo al propio registro) y EST-02 (consulta a los verificadores antes de borrar y propaga
 * el 409). EST-03 es de Bean Validation: ver {@code RequestsCatalogoValidacionTest} y {@code EstadioControllerTest}.
 * Mockito puro: sin Spring ni Docker. El mapper es el real (el generado por MapStruct).
 */
@ExtendWith(MockitoExtension.class)
class EstadioServiceImplTest {

    @Mock
    private EstadioRepository estadioRepository;

    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private final EstadioMapper estadioMapper = new EstadioMapperImpl();

    private EstadioServiceImpl servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new EstadioServiceImpl(estadioRepository, estadioMapper, verificadoresEliminacion);
    }

    private static Estadio estadio(Long id, String nombre, String ciudad, int capacidad) {
        Estadio estadio = new Estadio();
        estadio.setId(id);
        estadio.setNombre(nombre);
        estadio.setCiudad(ciudad);
        estadio.setCapacidad(capacidad);
        return estadio;
    }

    /** {@code save} y {@code saveAndFlush} devuelven la misma entidad; si no tiene id, le asignan el 10. */
    private void guardarDevuelveLaEntidad() {
        when(estadioRepository.save(any(Estadio.class))).thenAnswer(invocacion -> {
            Estadio guardado = invocacion.getArgument(0);
            guardado.setId(10L);
            return guardado;
        });
    }

    // ====================================================================== EST-01: crear

    @Test
    @DisplayName("EST-01 positivo: crea el estadio con el nombre y la ciudad normalizados (sin espacios sobrantes)")
    void est01_crear_nombreLibre_guardaNormalizado() {
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCase("Cali", "Pascual Guerrero")).thenReturn(false);
        guardarDevuelveLaEntidad();

        EstadioResponse respuesta = servicio.crear(new EstadioRequest("  Pascual    Guerrero ", "  Cali ", 38_000));

        assertThat(respuesta).isEqualTo(new EstadioResponse(10L, "Pascual Guerrero", "Cali", 38_000));
        ArgumentCaptor<Estadio> guardado = ArgumentCaptor.forClass(Estadio.class);
        verify(estadioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getNombre()).isEqualTo("Pascual Guerrero");
        assertThat(guardado.getValue().getCiudad()).isEqualTo("Cali");
    }

    @Test
    @DisplayName("EST-01 negativo: si ya hay un estadio con ese nombre en la ciudad, 409 con código EST-01 y no se guarda")
    void est01_crear_nombreDuplicadoEnLaCiudad_lanza409() {
        // La consulta ignora mayúsculas (IgnoreCase), así que se le pasa el texto tal cual, solo normalizado.
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCase("Cali", "pascual guerrero")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new EstadioRequest("  pascual guerrero ", "Cali", 100)))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("EST-01");
                    assertThat(ex.getMessage()).contains("pascual guerrero").contains("Cali");
                });

        verify(estadioRepository, never()).save(any());
    }

    @Test
    @DisplayName("EST-01: la comparación se hace con el texto ya normalizado, incluso con espacios internos repetidos")
    void est01_crear_comparaConElTextoNormalizado() {
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCase("Santiago de Cali", "Pascual Guerrero"))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new EstadioRequest("Pascual \t Guerrero", " Santiago   de   Cali ", 100)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("EST-01: un nombre formado solo por espacios no separables se rechaza con 422 antes de consultar la base")
    void est01_crear_nombreSoloConEspaciosNoSeparables_lanza422() {
        assertThatThrownBy(() -> servicio.crear(new EstadioRequest("  ", "Cali", 100)))
                .isInstanceOf(co.edu.uniajc.liga.exception.ReglaNegocioException.class);

        verifyNoInteractions(estadioRepository);
    }

    // ================================================================= EST-01: actualizar

    @Test
    @DisplayName("EST-01 positivo: actualizar conservando su propio nombre no es un duplicado (se excluye el propio registro)")
    void est01_actualizar_mismoNombreDelPropioRegistro_noEsDuplicado() {
        Estadio existente = estadio(3L, "Pascual Guerrero", "Cali", 100);
        when(estadioRepository.findById(3L)).thenReturn(Optional.of(existente));
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot("Cali", "Pascual Guerrero", 3L))
                .thenReturn(false);
        when(estadioRepository.saveAndFlush(existente)).thenReturn(existente);

        EstadioResponse respuesta = servicio.actualizar(3L, new EstadioRequest("Pascual Guerrero", "Cali", 200));

        assertThat(respuesta).isEqualTo(new EstadioResponse(3L, "Pascual Guerrero", "Cali", 200));
        // Se usa la consulta que excluye el id propio, nunca la que no lo excluye.
        verify(estadioRepository, never()).existsByCiudadIgnoreCaseAndNombreIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("EST-01 positivo: actualizar normaliza el nombre y la ciudad y conserva el id")
    void est01_actualizar_normalizaYConservaElId() {
        Estadio existente = estadio(3L, "Viejo", "Cali", 100);
        when(estadioRepository.findById(3L)).thenReturn(Optional.of(existente));
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot("Palmira", "Nuevo Nombre", 3L))
                .thenReturn(false);
        when(estadioRepository.saveAndFlush(existente)).thenReturn(existente);

        EstadioResponse respuesta = servicio.actualizar(3L, new EstadioRequest("  Nuevo   Nombre  ", " Palmira ", 500));

        assertThat(respuesta).isEqualTo(new EstadioResponse(3L, "Nuevo Nombre", "Palmira", 500));
    }

    @Test
    @DisplayName("EST-01 negativo: actualizar al nombre de OTRO estadio de la ciudad da 409 EST-01 y no modifica nada")
    void est01_actualizar_nombreDeOtroEstadio_lanza409() {
        Estadio existente = estadio(3L, "Pascual Guerrero", "Cali", 100);
        when(estadioRepository.findById(3L)).thenReturn(Optional.of(existente));
        when(estadioRepository.existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot("Cali", "Olímpico Pascual", 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(3L, new EstadioRequest("Olímpico Pascual", "Cali", 100)))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("EST-01"));

        verify(estadioRepository, never()).saveAndFlush(any());
        assertThat(existente.getNombre()).isEqualTo("Pascual Guerrero");
    }

    @Test
    @DisplayName("Actualizar un estadio inexistente: 404 y no se consulta la unicidad")
    void actualizar_noExiste_lanza404() {
        when(estadioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, new EstadioRequest("X", "Cali", 100)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Estadio").hasMessageContaining("99");

        verify(estadioRepository, never()).existsByCiudadIgnoreCaseAndNombreIgnoreCaseAndIdNot(any(), any(), any());
    }

    // ============================================================================== obtener

    @Test
    @DisplayName("obtener devuelve el estadio como DTO")
    void obtener_existente() {
        when(estadioRepository.findById(3L)).thenReturn(Optional.of(estadio(3L, "Pascual Guerrero", "Cali", 100)));

        assertThat(servicio.obtener(3L)).isEqualTo(new EstadioResponse(3L, "Pascual Guerrero", "Cali", 100));
    }

    @Test
    @DisplayName("obtener un estadio inexistente: 404 RECURSO_NO_ENCONTRADO")
    void obtener_noExiste_lanza404() {
        when(estadioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO"));
    }

    // =============================================================================== listar

    @Test
    @DisplayName("listar devuelve la página como DTO con su metadata")
    void listar_devuelveLaPagina() {
        List<Estadio> estadios = List.of(estadio(1L, "A", "Cali", 10), estadio(2L, "B", "Cali", 20));
        when(estadioRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(estadios, PageRequest.of(0, 2), 5));

        PaginaResponse<EstadioResponse> pagina = servicio.listar(PageRequest.of(0, 2));

        assertThat(pagina.contenido()).containsExactly(
                new EstadioResponse(1L, "A", "Cali", 10), new EstadioResponse(2L, "B", "Cali", 20));
        assertThat(pagina.totalElementos()).isEqualTo(5);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
    }

    @Test
    @DisplayName("listar normaliza la paginación: tamaño máximo 100 y orden desconocido reemplazado por el de por defecto")
    void listar_normalizaLaPaginacion() {
        when(estadioRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        servicio.listar(PageRequest.of(2, 5_000, Sort.by("passwordHash")));

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(estadioRepository).findAll(pedido.capture());
        assertThat(pedido.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pedido.getValue().getPageSize()).isEqualTo(100);
        assertThat(pedido.getValue().getSort()).isEqualTo(EstadioServiceImpl.ORDEN_POR_DEFECTO);
    }

    @Test
    @DisplayName("listar respeta un orden permitido (por ciudad, descendente)")
    void listar_respetaUnOrdenPermitido() {
        when(estadioRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        Sort pedido = Sort.by(Sort.Direction.DESC, "ciudad");

        servicio.listar(PageRequest.of(0, 10, pedido));

        ArgumentCaptor<Pageable> capturado = ArgumentCaptor.forClass(Pageable.class);
        verify(estadioRepository).findAll(capturado.capture());
        assertThat(capturado.getValue().getSort()).isEqualTo(pedido);
    }

    // ================================================================================ EST-02

    @Test
    @DisplayName("EST-02 positivo: sin dependientes, consulta a los verificadores y luego elimina el estadio")
    void est02_eliminar_sinDependientes_elimina() {
        Estadio existente = estadio(7L, "Pascual Guerrero", "Cali", 100);
        when(estadioRepository.findById(7L)).thenReturn(Optional.of(existente));

        servicio.eliminar(7L);

        InOrder orden = inOrder(verificadoresEliminacion, estadioRepository);
        orden.verify(verificadoresEliminacion).verificar(Estadio.class, 7L);
        orden.verify(estadioRepository).delete(existente);
        orden.verify(estadioRepository).flush();
    }

    @Test
    @DisplayName("EST-02 negativo: si un verificador detecta equipos o partidos, el 409 EST-02 se propaga y no se elimina")
    void est02_eliminar_conDependientes_lanza409YNoElimina() {
        Estadio existente = estadio(7L, "Pascual Guerrero", "Cali", 100);
        when(estadioRepository.findById(7L)).thenReturn(Optional.of(existente));
        doThrow(new ConflictoException("EST-02", "El estadio es local de al menos un equipo"))
                .when(verificadoresEliminacion).verificar(Estadio.class, 7L);

        assertThatThrownBy(() -> servicio.eliminar(7L))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));

        verify(estadioRepository, never()).delete(any(Estadio.class));
    }

    @Test
    @DisplayName("EST-02: eliminar un estadio inexistente da 404 y no consulta a los verificadores")
    void est02_eliminar_noExiste_lanza404() {
        when(estadioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(verificadoresEliminacion);
        verify(estadioRepository, never()).delete(any(Estadio.class));
    }
}

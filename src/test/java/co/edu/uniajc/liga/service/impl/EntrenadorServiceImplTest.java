package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.EntrenadorMapper;
import co.edu.uniajc.liga.mapper.EntrenadorMapperImpl;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
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
 * Reglas del servicio de entrenadores: normalización de textos, 404 y consulta a los verificadores antes de
 * borrar (el entrenador asignado a un equipo da 409 cuando existe el verificador de la fase de equipos).
 * Mockito puro.
 */
@ExtendWith(MockitoExtension.class)
class EntrenadorServiceImplTest {

    @Mock
    private EntrenadorRepository entrenadorRepository;

    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private final EntrenadorMapper entrenadorMapper = new EntrenadorMapperImpl();

    private EntrenadorServiceImpl servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new EntrenadorServiceImpl(entrenadorRepository, entrenadorMapper, verificadoresEliminacion);
    }

    private static Entrenador entrenador(Long id, String nombre, String nacionalidad) {
        Entrenador entrenador = new Entrenador();
        entrenador.setId(id);
        entrenador.setNombre(nombre);
        entrenador.setNacionalidad(nacionalidad);
        return entrenador;
    }

    @Test
    @DisplayName("Crear un entrenador positivo: normaliza el nombre y la nacionalidad y devuelve el DTO con su id")
    void crear_normalizaYGuarda() {
        when(entrenadorRepository.save(any(Entrenador.class))).thenAnswer(invocacion -> {
            Entrenador guardado = invocacion.getArgument(0);
            guardado.setId(8L);
            return guardado;
        });

        EntrenadorResponse respuesta = servicio.crear(new EntrenadorRequest("  Alexandre   Guimarães ", " Brasil "));

        assertThat(respuesta).isEqualTo(new EntrenadorResponse(8L, "Alexandre Guimarães", "Brasil"));
    }

    @Test
    @DisplayName("Crear un entrenador: una nacionalidad con solo espacios se guarda como null")
    void crear_nacionalidadEnBlancoQuedaNula() {
        when(entrenadorRepository.save(any(Entrenador.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertThat(servicio.crear(new EntrenadorRequest("Alexandre", "  ")).nacionalidad()).isNull();
    }

    @Test
    @DisplayName("Crear un entrenador negativo: un nombre solo con espacios no separables se rechaza con 422 sin guardar")
    void crear_nombreSoloConEspaciosNoSeparables_lanza422() {
        assertThatThrownBy(() -> servicio.crear(new EntrenadorRequest(" ", "Brasil")))
                .isInstanceOf(co.edu.uniajc.liga.exception.ReglaNegocioException.class);

        verifyNoInteractions(entrenadorRepository);
    }

    @Test
    @DisplayName("Actualizar un entrenador positivo: reemplaza los datos normalizados y conserva el id")
    void actualizar_reemplazaLosDatos() {
        Entrenador existente = entrenador(8L, "Viejo", "Chile");
        when(entrenadorRepository.findById(8L)).thenReturn(Optional.of(existente));
        when(entrenadorRepository.saveAndFlush(existente)).thenReturn(existente);

        EntrenadorResponse respuesta = servicio.actualizar(8L, new EntrenadorRequest("  Nuevo  Nombre ", "Perú"));

        assertThat(respuesta).isEqualTo(new EntrenadorResponse(8L, "Nuevo Nombre", "Perú"));
    }

    @Test
    @DisplayName("Actualizar un entrenador inexistente: 404")
    void actualizar_noExiste_lanza404() {
        when(entrenadorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, new EntrenadorRequest("X", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(entrenadorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Obtener: positivo (DTO) y negativo (404)")
    void obtener() {
        when(entrenadorRepository.findById(8L)).thenReturn(Optional.of(entrenador(8L, "Alexandre", "Brasil")));
        when(entrenadorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(8L)).isEqualTo(new EntrenadorResponse(8L, "Alexandre", "Brasil"));
        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar devuelve la página como DTO y normaliza la paginación (máx. 100, orden desconocido descartado)")
    void listar_normalizaLaPaginacion() {
        when(entrenadorRepository.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(entrenador(1L, "A", null)), PageRequest.of(0, 20), 1));

        PaginaResponse<EntrenadorResponse> pagina = servicio.listar(PageRequest.of(0, 9_999, Sort.by("hack")));

        assertThat(pagina.contenido()).containsExactly(new EntrenadorResponse(1L, "A", null));
        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(entrenadorRepository).findAll(pedido.capture());
        assertThat(pedido.getValue().getPageSize()).isEqualTo(100);
        assertThat(pedido.getValue().getSort()).isEqualTo(EntrenadorServiceImpl.ORDEN_POR_DEFECTO);
    }

    @Test
    @DisplayName("Eliminar positivo: sin dependientes, consulta a los verificadores de Entrenador y elimina")
    void eliminar_sinDependientes_elimina() {
        Entrenador existente = entrenador(8L, "Alexandre", null);
        when(entrenadorRepository.findById(8L)).thenReturn(Optional.of(existente));

        servicio.eliminar(8L);

        InOrder orden = inOrder(verificadoresEliminacion, entrenadorRepository);
        orden.verify(verificadoresEliminacion).verificar(Entrenador.class, 8L);
        orden.verify(entrenadorRepository).delete(existente);
    }

    @Test
    @DisplayName("Eliminar negativo: si el entrenador dirige a un equipo, el 409 del verificador se propaga y no se elimina")
    void eliminar_conEquipo_lanza409() {
        when(entrenadorRepository.findById(8L)).thenReturn(Optional.of(entrenador(8L, "Alexandre", null)));
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "El entrenador dirige a un equipo"))
                .when(verificadoresEliminacion).verificar(Entrenador.class, 8L);

        assertThatThrownBy(() -> servicio.eliminar(8L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));

        verify(entrenadorRepository, never()).delete(any(Entrenador.class));
    }

    @Test
    @DisplayName("Eliminar un entrenador inexistente: 404 sin consultar a los verificadores")
    void eliminar_noExiste_lanza404() {
        when(entrenadorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(verificadoresEliminacion);
    }
}

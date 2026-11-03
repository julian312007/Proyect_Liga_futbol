package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.ArbitroMapper;
import co.edu.uniajc.liga.mapper.ArbitroMapperImpl;
import co.edu.uniajc.liga.repository.ArbitroRepository;
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
 * Reglas del servicio de árbitros: normalización de textos, 404 y consulta a los verificadores antes de borrar
 * (el árbitro con partidos da 409 cuando existe el verificador de la fase de partidos). Mockito puro.
 */
@ExtendWith(MockitoExtension.class)
class ArbitroServiceImplTest {

    @Mock
    private ArbitroRepository arbitroRepository;

    @Mock
    private VerificadoresEliminacion verificadoresEliminacion;

    private final ArbitroMapper arbitroMapper = new ArbitroMapperImpl();

    private ArbitroServiceImpl servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new ArbitroServiceImpl(arbitroRepository, arbitroMapper, verificadoresEliminacion);
    }

    private static Arbitro arbitro(Long id, String nombre, String nacionalidad, int anios) {
        Arbitro arbitro = new Arbitro();
        arbitro.setId(id);
        arbitro.setNombre(nombre);
        arbitro.setNacionalidad(nacionalidad);
        arbitro.setAniosExperiencia(anios);
        return arbitro;
    }

    @Test
    @DisplayName("Crear un árbitro positivo: normaliza el nombre y la nacionalidad y devuelve el DTO con su id")
    void crear_normalizaYGuarda() {
        when(arbitroRepository.save(any(Arbitro.class))).thenAnswer(invocacion -> {
            Arbitro guardado = invocacion.getArgument(0);
            guardado.setId(5L);
            return guardado;
        });

        ArbitroResponse respuesta = servicio.crear(new ArbitroRequest("  Wilmar   Roldán ", "  Colombia ", 15));

        assertThat(respuesta).isEqualTo(new ArbitroResponse(5L, "Wilmar Roldán", "Colombia", 15));
    }

    @Test
    @DisplayName("Crear un árbitro: una nacionalidad vacía o con solo espacios se guarda como null")
    void crear_nacionalidadEnBlancoQuedaNula() {
        when(arbitroRepository.save(any(Arbitro.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ArbitroResponse respuesta = servicio.crear(new ArbitroRequest("Wilmar Roldán", "   ", 0));

        assertThat(respuesta.nacionalidad()).isNull();
        assertThat(respuesta.aniosExperiencia()).isZero();
    }

    @Test
    @DisplayName("Actualizar un árbitro positivo: reemplaza los datos normalizados y conserva el id")
    void actualizar_reemplazaLosDatos() {
        Arbitro existente = arbitro(5L, "Viejo", "Chile", 3);
        when(arbitroRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(arbitroRepository.saveAndFlush(existente)).thenReturn(existente);

        ArbitroResponse respuesta = servicio.actualizar(5L, new ArbitroRequest("  Nuevo  Nombre ", null, 4));

        assertThat(respuesta).isEqualTo(new ArbitroResponse(5L, "Nuevo Nombre", null, 4));
    }

    @Test
    @DisplayName("Actualizar un árbitro inexistente: 404")
    void actualizar_noExiste_lanza404() {
        when(arbitroRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, new ArbitroRequest("X", null, 1)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(arbitroRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Obtener: positivo (DTO) y negativo (404)")
    void obtener() {
        when(arbitroRepository.findById(5L)).thenReturn(Optional.of(arbitro(5L, "Wilmar Roldán", "Colombia", 15)));
        when(arbitroRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(servicio.obtener(5L)).isEqualTo(new ArbitroResponse(5L, "Wilmar Roldán", "Colombia", 15));
        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar devuelve la página como DTO y normaliza la paginación (máx. 100, orden desconocido descartado)")
    void listar_normalizaLaPaginacion() {
        when(arbitroRepository.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(arbitro(1L, "A", null, 1)), PageRequest.of(0, 20), 1));

        PaginaResponse<ArbitroResponse> pagina = servicio.listar(PageRequest.of(0, 9_999, Sort.by("hack")));

        assertThat(pagina.contenido()).containsExactly(new ArbitroResponse(1L, "A", null, 1));
        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(arbitroRepository).findAll(pedido.capture());
        assertThat(pedido.getValue().getPageSize()).isEqualTo(100);
        assertThat(pedido.getValue().getSort()).isEqualTo(ArbitroServiceImpl.ORDEN_POR_DEFECTO);
    }

    @Test
    @DisplayName("Eliminar positivo: sin dependientes, consulta a los verificadores de Arbitro y elimina")
    void eliminar_sinDependientes_elimina() {
        Arbitro existente = arbitro(5L, "Wilmar Roldán", null, 1);
        when(arbitroRepository.findById(5L)).thenReturn(Optional.of(existente));

        servicio.eliminar(5L);

        InOrder orden = inOrder(verificadoresEliminacion, arbitroRepository);
        orden.verify(verificadoresEliminacion).verificar(Arbitro.class, 5L);
        orden.verify(arbitroRepository).delete(existente);
    }

    @Test
    @DisplayName("Eliminar negativo: si el árbitro tiene partidos, el 409 del verificador se propaga y no se elimina")
    void eliminar_conPartidos_lanza409() {
        when(arbitroRepository.findById(5L)).thenReturn(Optional.of(arbitro(5L, "Wilmar Roldán", null, 1)));
        doThrow(new ConflictoException("CONFLICTO_DEPENDENCIAS", "El árbitro tiene partidos"))
                .when(verificadoresEliminacion).verificar(Arbitro.class, 5L);

        assertThatThrownBy(() -> servicio.eliminar(5L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));

        verify(arbitroRepository, never()).delete(any(Arbitro.class));
    }

    @Test
    @DisplayName("Eliminar un árbitro inexistente: 404 sin consultar a los verificadores")
    void eliminar_noExiste_lanza404() {
        when(arbitroRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(verificadoresEliminacion);
    }
}

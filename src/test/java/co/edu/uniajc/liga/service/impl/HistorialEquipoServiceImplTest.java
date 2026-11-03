package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.RecursoNoEncontradoException;
import co.edu.uniajc.liga.mapper.HistorialPartidoMapper;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadisticasPartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static co.edu.uniajc.liga.support.FabricaEstadisticas.equipo;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.liga;
import static co.edu.uniajc.liga.support.FabricaEstadisticas.partidoDeLiga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Historial de partidos de un equipo (F6): 404, paginación segura y mapeo. Repositorios simulados. */
@ExtendWith(MockitoExtension.class)
class HistorialEquipoServiceImplTest {

    private static final long EQUIPO = 1L;

    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private EstadisticasPartidoRepository partidoRepository;

    private HistorialEquipoServiceImpl servicio;

    private final Liga liga = liga(7, "Liga Águila 2026");
    private final Equipo aguilas = equipo(EQUIPO, "Águilas");
    private final Equipo buhos = equipo(2, "Búhos");

    @BeforeEach
    void crearServicio() {
        servicio = new HistorialEquipoServiceImpl(equipoRepository, partidoRepository, new HistorialPartidoMapper());
    }

    @Test
    @DisplayName("Devuelve la página de partidos del equipo, vistos desde ese equipo, con los totales de la página")
    void devuelveLaPaginaConElResultadoDesdeElEquipo() {
        Partido jugado = partidoDeLiga(50, liga, 1, Instant.parse("2026-03-01T20:00:00Z"), aguilas, buhos, 2, 0,
                EstadoPartido.FINALIZADO);
        Partido programado = partidoDeLiga(51, liga, 2, Instant.parse("2026-03-08T20:00:00Z"), buhos, aguilas, 0, 0,
                EstadoPartido.PROGRAMADO);
        Pageable pedido = PageRequest.of(0, 10);
        when(equipoRepository.existsById(EQUIPO)).thenReturn(true);
        when(partidoRepository.buscarHistorialDeEquipo(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(programado, jugado), PageRequest.of(0, 10), 2));

        PaginaResponse<PartidoHistorialResponse> pagina = servicio.obtenerHistorial(EQUIPO, pedido);

        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(pagina.totalPaginas()).isEqualTo(1);
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamanio()).isEqualTo(10);
        assertThat(pagina.contenido()).extracting(PartidoHistorialResponse::id).containsExactly(51L, 50L);
        // El programado no tiene resultado y el equipo juega de visitante; el jugado fue una victoria de local.
        assertThat(pagina.contenido().get(0).esLocal()).isFalse();
        assertThat(pagina.contenido().get(0).resultado()).isNull();
        assertThat(pagina.contenido().get(1).esLocal()).isTrue();
        assertThat(pagina.contenido().get(1).resultado()).isEqualTo(PartidoHistorialResponse.Resultado.VICTORIA);
    }

    @Test
    @DisplayName("Sin orden pedido se usan los partidos más recientes primero")
    void sinOrden_usaFechaHoraDescendente() {
        Pageable usado = consultarYCapturar(PageRequest.of(2, 5));

        assertThat(usado.getPageNumber()).isEqualTo(2);
        assertThat(usado.getPageSize()).isEqualTo(5);
        assertThat(usado.getSort()).isEqualTo(Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("Un orden permitido se respeta")
    void ordenPermitido_seRespeta() {
        Pageable usado = consultarYCapturar(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "jornada")));

        assertThat(usado.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "jornada"));
    }

    @Test
    @DisplayName("Un orden por una propiedad no permitida se cambia por el orden por defecto, en vez de fallar")
    void ordenNoPermitido_seReemplazaPorElOrdenPorDefecto() {
        Pageable usado = consultarYCapturar(PageRequest.of(0, 20, Sort.by("estadio.nombre")));

        assertThat(usado.getSort()).isEqualTo(Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("El tamaño de página se limita a 100")
    void tamanioGigante_seLimitaACien() {
        Pageable usado = consultarYCapturar(PageRequest.of(0, 100_000));

        assertThat(usado.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Equipo inexistente: RecursoNoEncontradoException (404) y no se consultan partidos")
    void equipoInexistente_lanza404() {
        when(equipoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.obtenerHistorial(99L, PageRequest.of(0, 10)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Equipo")
                .hasMessageContaining("99");

        verifyNoInteractions(partidoRepository);
    }

    private Pageable consultarYCapturar(Pageable pedido) {
        Page<Partido> vacia = new PageImpl<>(List.of());
        when(equipoRepository.existsById(EQUIPO)).thenReturn(true);
        when(partidoRepository.buscarHistorialDeEquipo(any(Long.class), any(Pageable.class))).thenReturn(vacia);

        servicio.obtenerHistorial(EQUIPO, pedido);

        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository).buscarHistorialDeEquipo(any(Long.class), captura.capture());
        return captura.getValue();
    }
}

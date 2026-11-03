package co.edu.uniajc.liga.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de {@link PaginacionSegura} (pieza compartida del dominio base): limita el tamaño de página y solo
 * acepta ordenar por propiedades conocidas. Sin Spring ni Docker.
 */
class PaginacionSeguraTest {

    private static final Set<String> PERMITIDAS = Set.of("id", "nombre", "ciudad");
    private static final Sort POR_DEFECTO = Sort.by("nombre");

    @Test
    @DisplayName("Sin paginación (null): primera página de 20 elementos con el orden por defecto")
    void pageableNulo_devuelvePrimeraPaginaPorDefecto() {
        Pageable resultado = PaginacionSegura.normalizar(null, PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getPageNumber()).isZero();
        assertThat(resultado.getPageSize()).isEqualTo(PaginacionSegura.TAMANIO_POR_DEFECTO).isEqualTo(20);
        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
    }

    @Test
    @DisplayName("Pageable.unpaged(): se trata como no enviado (no se devuelve toda la tabla)")
    void pageableSinPaginar_devuelvePrimeraPaginaPorDefecto() {
        Pageable resultado = PaginacionSegura.normalizar(Pageable.unpaged(), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.isPaged()).isTrue();
        assertThat(resultado.getPageSize()).isEqualTo(20);
        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
    }

    @Test
    @DisplayName("Un tamaño por encima del máximo se recorta a 100")
    void tamanioExcesivo_seRecortaAlMaximo() {
        Pageable resultado = PaginacionSegura.normalizar(PageRequest.of(0, 100_000), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getPageSize()).isEqualTo(PaginacionSegura.TAMANIO_MAXIMO).isEqualTo(100);
    }

    @Test
    @DisplayName("Un tamaño dentro del límite (1 y 100) se respeta")
    void tamanioValido_seRespeta() {
        assertThat(PaginacionSegura.normalizar(PageRequest.of(0, 1), PERMITIDAS, POR_DEFECTO).getPageSize()).isEqualTo(1);
        assertThat(PaginacionSegura.normalizar(PageRequest.of(0, 100), PERMITIDAS, POR_DEFECTO).getPageSize()).isEqualTo(100);
        assertThat(PaginacionSegura.normalizar(PageRequest.of(0, 37), PERMITIDAS, POR_DEFECTO).getPageSize()).isEqualTo(37);
    }

    @Test
    @DisplayName("El número de página se conserva")
    void numeroDePagina_seConserva() {
        Pageable resultado = PaginacionSegura.normalizar(PageRequest.of(7, 10), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getPageNumber()).isEqualTo(7);
    }

    @Test
    @DisplayName("Un orden por una propiedad permitida se respeta, con su dirección")
    void ordenPermitido_seRespeta() {
        Sort pedido = Sort.by(Sort.Order.desc("ciudad"), Sort.Order.asc("id"));

        Pageable resultado = PaginacionSegura.normalizar(PageRequest.of(0, 10, pedido), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getSort()).isEqualTo(pedido);
    }

    @Test
    @DisplayName("Un orden por una propiedad desconocida se descarta: se usa el orden por defecto (evita un 500)")
    void ordenNoPermitido_usaElOrdenPorDefecto() {
        Pageable resultado = PaginacionSegura.normalizar(
                PageRequest.of(0, 10, Sort.by("passwordHash")), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
    }

    @Test
    @DisplayName("Si una sola de las propiedades pedidas no está permitida, se descarta todo el orden pedido")
    void ordenMixto_usaElOrdenPorDefecto() {
        Pageable resultado = PaginacionSegura.normalizar(
                PageRequest.of(0, 10, Sort.by("nombre", "noExiste")), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
    }

    @Test
    @DisplayName("Sin orden pedido: se usa el orden por defecto")
    void sinOrden_usaElOrdenPorDefecto() {
        Pageable resultado = PaginacionSegura.normalizar(PageRequest.of(2, 15), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
        assertThat(resultado.getPageNumber()).isEqualTo(2);
        assertThat(resultado.getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("Recortar el tamaño y descartar el orden ocurren a la vez")
    void tamanioYOrdenInvalidos_seCorrigenJuntos() {
        Pageable resultado = PaginacionSegura.normalizar(
                PageRequest.of(3, 5_000, Sort.by("inyeccion")), PERMITIDAS, POR_DEFECTO);

        assertThat(resultado.getPageNumber()).isEqualTo(3);
        assertThat(resultado.getPageSize()).isEqualTo(100);
        assertThat(resultado.getSort()).isEqualTo(POR_DEFECTO);
    }
}

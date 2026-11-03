package co.edu.uniajc.liga.dto;

import co.edu.uniajc.liga.dto.response.PaginaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de {@link PaginaResponse} (pieza compartida del dominio base): la forma JSON de los listados
 * paginados debe ser estable y propia, no la de {@code PageImpl}. Sin Spring ni Docker.
 */
class PaginaResponseTest {

    @Test
    @DisplayName("de(Page) copia contenido, página, tamaño, total de elementos y total de páginas")
    void de_copiaLosDatosDeLaPagina() {
        PageImpl<String> pagina = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PaginaResponse<String> respuesta = PaginaResponse.de(pagina);

        assertThat(respuesta.contenido()).containsExactly("a", "b");
        assertThat(respuesta.pagina()).isEqualTo(1);
        assertThat(respuesta.tamanio()).isEqualTo(2);
        assertThat(respuesta.totalElementos()).isEqualTo(5);
        assertThat(respuesta.totalPaginas()).isEqualTo(3);
    }

    @Test
    @DisplayName("de(Page) con una página vacía: contenido vacío y cero elementos")
    void de_paginaVacia() {
        PageImpl<String> vacia = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PaginaResponse<String> respuesta = PaginaResponse.de(vacia);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.pagina()).isZero();
        assertThat(respuesta.tamanio()).isEqualTo(20);
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.totalPaginas()).isZero();
    }

    @Test
    @DisplayName("de(Page) conserva el tipo de los elementos (por ejemplo, un DTO) y la numeración desde 0")
    void de_conservaElTipoDeLosElementos() {
        PageImpl<Integer> pagina = new PageImpl<>(List.of(10, 20, 30), PageRequest.of(0, 3), 3);

        PaginaResponse<Integer> respuesta = PaginaResponse.de(pagina);

        assertThat(respuesta.contenido()).containsExactly(10, 20, 30);
        assertThat(respuesta.pagina()).isZero();
        assertThat(respuesta.totalPaginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("La forma JSON es estable: contenido, pagina, tamanio, totalElementos y totalPaginas")
    void formaDeLaRespuesta_esEstable() {
        List<String> componentes = Arrays.stream(PaginaResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentes)
                .containsExactly("contenido", "pagina", "tamanio", "totalElementos", "totalPaginas");
    }
}

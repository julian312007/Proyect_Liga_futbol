package co.edu.uniajc.liga.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página de resultados con una forma JSON estable y propia (no se serializa {@code PageImpl} de Spring
 * Data, cuya estructura no está garantizada). {@code pagina} empieza en 0.
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}

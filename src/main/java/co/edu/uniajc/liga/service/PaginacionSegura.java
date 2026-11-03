package co.edu.uniajc.liga.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Normaliza el {@link Pageable} que llega del cliente antes de consultar: limita el tamaño de página y solo
 * acepta ordenar por propiedades conocidas. Sin esto, {@code ?sort=cualquierCosa} haría que Spring Data lance
 * {@code PropertyReferenceException} (un 500) y {@code ?size=100000} traería una tabla completa.
 */
public final class PaginacionSegura {

    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO = 100;

    private PaginacionSegura() {
    }

    /**
     * @param pageable        el que resolvió Spring MVC (puede traer cualquier orden y tamaño)
     * @param permitidas      nombres de las propiedades de la entidad por las que se acepta ordenar
     * @param ordenPorDefecto orden que se usa si el cliente no pide ninguno o pide uno no permitido
     */
    public static Pageable normalizar(Pageable pageable, Set<String> permitidas, Sort ordenPorDefecto) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, TAMANIO_POR_DEFECTO, ordenPorDefecto);
        }
        int tamanio = Math.max(1, Math.min(pageable.getPageSize(), TAMANIO_MAXIMO));
        Sort pedido = pageable.getSort();
        boolean ordenValido = pedido.isSorted() && pedido.stream().allMatch(o -> permitidas.contains(o.getProperty()));
        return PageRequest.of(pageable.getPageNumber(), tamanio, ordenValido ? pedido : ordenPorDefecto);
    }
}

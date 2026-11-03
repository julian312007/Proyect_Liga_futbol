package co.edu.uniajc.liga.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Reúne todos los {@link VerificadorEliminacion} registrados y consulta los de una entidad. Los servicios
 * llaman a {@link #verificar(Class, Long)} justo antes de borrar.
 *
 * <p>Los verificadores se obtienen al momento de usarlos (por {@link ObjectProvider}), no al construir el
 * servicio: así no hay dependencias circulares entre un servicio y los repositorios de otras fases, y
 * funciona aunque todavía no exista ningún verificador.
 */
@Component
public class VerificadoresEliminacion {

    private final ObjectProvider<VerificadorEliminacion> verificadores;

    public VerificadoresEliminacion(ObjectProvider<VerificadorEliminacion> verificadores) {
        this.verificadores = verificadores;
    }

    /** Ejecuta los verificadores de {@code entidad}; el primero que detecte dependientes lanza el 409. */
    public void verificar(Class<?> entidad, Long id) {
        verificadores.orderedStream()
                .filter(verificador -> verificador.entidad().equals(entidad))
                .forEach(verificador -> verificador.verificar(id));
    }
}

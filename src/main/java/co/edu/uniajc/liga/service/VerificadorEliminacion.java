package co.edu.uniajc.liga.service;

/**
 * Punto de extensión para impedir el borrado de una entidad que tiene datos dependientes (HTTP 409).
 *
 * <p>Los servicios de catálogo (Fase 2) no conocen a los equipos ni a los partidos, que llegan en fases
 * posteriores. En lugar de editar esos servicios, cada fase posterior publica un bean que implementa esta
 * interfaz para la entidad que le interesa, y {@link VerificadoresEliminacion} los consulta antes de borrar.
 *
 * <p>Una implementación lanza {@code ConflictoException} (con el código de la regla, por ejemplo
 * {@code EST-02}) si la entidad {@code id} no puede eliminarse.
 */
public interface VerificadorEliminacion {

    /** Tipo de entidad que vigila, por ejemplo {@code Estadio.class}. */
    Class<?> entidad();

    /** Lanza {@code ConflictoException} si la entidad con ese id tiene dependientes. */
    void verificar(Long id);
}

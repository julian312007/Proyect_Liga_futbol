package co.edu.uniajc.liga.service;

import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.exception.ConflictoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Prueba de {@link VerificadoresEliminacion} (pieza compartida del dominio base) con un mock de
 * {@link ObjectProvider}: consulta solo a los verificadores de la entidad pedida y propaga su 409.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificadoresEliminacionTest {

    @Mock
    private ObjectProvider<VerificadorEliminacion> proveedor;

    private VerificadoresEliminacion verificadores;

    @BeforeEach
    void crearServicio() {
        verificadores = new VerificadoresEliminacion(proveedor);
    }

    private VerificadorEliminacion verificadorDe(Class<?> entidad) {
        VerificadorEliminacion verificador = mock(VerificadorEliminacion.class);
        when(verificador.entidad()).thenAnswer(invocacion -> entidad);
        return verificador;
    }

    /** Un {@code Stream} solo se recorre una vez: cada llamada a {@code orderedStream()} recibe uno nuevo. */
    private void registrar(VerificadorEliminacion... registrados) {
        when(proveedor.orderedStream()).thenAnswer(invocacion -> List.of(registrados).stream());
    }

    @Test
    @DisplayName("Sin ningún verificador registrado (Fase 2 sola): no hace nada y no falla")
    void sinVerificadores_noHaceNada() {
        registrar();

        assertThatCode(() -> verificadores.verificar(Estadio.class, 5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ejecuta el verificador de la entidad pedida con el id recibido")
    void ejecutaElVerificadorDeLaEntidad() {
        VerificadorEliminacion deEstadio = verificadorDe(Estadio.class);
        registrar(deEstadio);

        verificadores.verificar(Estadio.class, 5L);

        verify(deEstadio).verificar(5L);
    }

    @Test
    @DisplayName("No ejecuta los verificadores de otras entidades")
    void ignoraLosVerificadoresDeOtrasEntidades() {
        VerificadorEliminacion deEstadio = verificadorDe(Estadio.class);
        VerificadorEliminacion deLiga = verificadorDe(Liga.class);
        registrar(deEstadio, deLiga);

        verificadores.verificar(Estadio.class, 5L);

        verify(deEstadio).verificar(5L);
        verify(deLiga, never()).verificar(anyLong());
    }

    @Test
    @DisplayName("Si el verificador detecta dependientes, el ConflictoException (409) se propaga con su código")
    void propagaElConflicto() {
        VerificadorEliminacion deEstadio = verificadorDe(Estadio.class);
        doThrow(new ConflictoException("EST-02", "El estadio es local de un equipo"))
                .when(deEstadio).verificar(5L);
        registrar(deEstadio);

        assertThatThrownBy(() -> verificadores.verificar(Estadio.class, 5L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));
    }

    @Test
    @DisplayName("Con varios verificadores de la misma entidad se ejecutan todos, en el orden del proveedor")
    void ejecutaTodosLosDeLaMismaEntidadEnOrden() {
        VerificadorEliminacion primero = verificadorDe(Estadio.class);
        VerificadorEliminacion segundo = verificadorDe(Estadio.class);
        registrar(primero, segundo);

        verificadores.verificar(Estadio.class, 9L);

        InOrder orden = inOrder(primero, segundo);
        orden.verify(primero).verificar(9L);
        orden.verify(segundo).verificar(9L);
    }

    @Test
    @DisplayName("El primer verificador que lanza el 409 detiene la cadena: los siguientes no se ejecutan")
    void elPrimerConflictoDetieneLaCadena() {
        VerificadorEliminacion primero = verificadorDe(Estadio.class);
        VerificadorEliminacion segundo = verificadorDe(Estadio.class);
        doThrow(new ConflictoException("EST-02", "Tiene equipos")).when(primero).verificar(9L);
        registrar(primero, segundo);

        assertThatThrownBy(() -> verificadores.verificar(Estadio.class, 9L)).isInstanceOf(ConflictoException.class);

        verify(segundo, never()).verificar(anyLong());
    }

    @Test
    @DisplayName("Consulta los verificadores al momento de usarlos (orderedStream), no al construirse")
    void consultaElProveedorAlUsarlo() {
        registrar();

        verificadores.verificar(Estadio.class, 1L);
        verificadores.verificar(Estadio.class, 2L);

        verify(proveedor, org.mockito.Mockito.times(2)).orderedStream();
    }
}

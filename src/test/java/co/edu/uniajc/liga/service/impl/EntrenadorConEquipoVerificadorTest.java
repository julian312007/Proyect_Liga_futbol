package co.edu.uniajc.liga.service.impl;

import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EquipoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Un entrenador asignado a un equipo no se elimina. Sin Spring ni base de datos. */
@ExtendWith(MockitoExtension.class)
class EntrenadorConEquipoVerificadorTest {

    @Mock
    private EquipoRepository equipoRepository;

    @InjectMocks
    private EntrenadorConEquipoVerificador verificador;

    @Test
    @DisplayName("Vigila la entidad Entrenador")
    void entidad_esEntrenador() {
        assertThat(verificador.entidad()).isEqualTo(Entrenador.class);
    }

    @Test
    @DisplayName("Entrenador positivo: un entrenador sin equipo se puede eliminar")
    void entrenadorSinEquipo_noLanzaNada() {
        when(equipoRepository.existsByEntrenadorId(7L)).thenReturn(false);

        assertThatCode(() -> verificador.verificar(7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Entrenador negativo: un entrenador asignado a un equipo lanza 409 CONFLICTO_DEPENDENCIAS")
    void entrenadorConEquipo_lanzaConflicto() {
        when(equipoRepository.existsByEntrenadorId(7L)).thenReturn(true);

        assertThatThrownBy(() -> verificador.verificar(7L))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
    }
}

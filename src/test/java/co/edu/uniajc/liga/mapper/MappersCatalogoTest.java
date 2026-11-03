package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los mappers de los catálogos, sin Spring (se usan las implementaciones que genera MapStruct).
 * Comprueban lo que importa: el {@code id} nunca viaja del cliente a la entidad y los campos se copian.
 */
class MappersCatalogoTest {

    private final EstadioMapper estadioMapper = new EstadioMapperImpl();
    private final ArbitroMapper arbitroMapper = new ArbitroMapperImpl();
    private final EntrenadorMapper entrenadorMapper = new EntrenadorMapperImpl();
    private final LigaMapper ligaMapper = new LigaMapperImpl();

    // ------------------------------------------------------------------ Estadio

    @Test
    @DisplayName("EstadioMapper.toEntity copia nombre, ciudad y capacidad y deja el id sin asignar")
    void estadio_toEntity() {
        Estadio estadio = estadioMapper.toEntity(new EstadioRequest("Pascual Guerrero", "Cali", 38_000));

        assertThat(estadio.getId()).isNull();
        assertThat(estadio.getNombre()).isEqualTo("Pascual Guerrero");
        assertThat(estadio.getCiudad()).isEqualTo("Cali");
        assertThat(estadio.getCapacidad()).isEqualTo(38_000);
    }

    @Test
    @DisplayName("EstadioMapper.toResponse copia todos los campos, incluido el id")
    void estadio_toResponse() {
        Estadio estadio = new Estadio();
        estadio.setId(4L);
        estadio.setNombre("Pascual Guerrero");
        estadio.setCiudad("Cali");
        estadio.setCapacidad(38_000);

        assertThat(estadioMapper.toResponse(estadio))
                .isEqualTo(new EstadioResponse(4L, "Pascual Guerrero", "Cali", 38_000));
    }

    @Test
    @DisplayName("EstadioMapper.updateEntity reemplaza los datos y conserva el id")
    void estadio_updateEntity_conservaElId() {
        Estadio estadio = new Estadio();
        estadio.setId(4L);
        estadio.setNombre("Viejo");
        estadio.setCiudad("Cali");
        estadio.setCapacidad(10);

        estadioMapper.updateEntity(new EstadioRequest("Nuevo", "Palmira", 20), estadio);

        assertThat(estadio.getId()).isEqualTo(4L);
        assertThat(estadio.getNombre()).isEqualTo("Nuevo");
        assertThat(estadio.getCiudad()).isEqualTo("Palmira");
        assertThat(estadio.getCapacidad()).isEqualTo(20);
    }

    @Test
    @DisplayName("Los mappers aceptan null y devuelven null")
    void mappers_aceptanNulo() {
        assertThat(estadioMapper.toEntity(null)).isNull();
        assertThat(estadioMapper.toResponse(null)).isNull();
        assertThat(arbitroMapper.toEntity(null)).isNull();
        assertThat(arbitroMapper.toResponse(null)).isNull();
        assertThat(entrenadorMapper.toEntity(null)).isNull();
        assertThat(entrenadorMapper.toResponse(null)).isNull();
        assertThat(ligaMapper.toEntity(null)).isNull();
        assertThat(ligaMapper.toResponse(null)).isNull();
    }

    // ------------------------------------------------------------------ Arbitro

    @Test
    @DisplayName("ArbitroMapper.toEntity copia los campos y deja el id sin asignar")
    void arbitro_toEntity() {
        Arbitro arbitro = arbitroMapper.toEntity(new ArbitroRequest("Wilmar Roldán", "Colombia", 15));

        assertThat(arbitro.getId()).isNull();
        assertThat(arbitro.getNombre()).isEqualTo("Wilmar Roldán");
        assertThat(arbitro.getNacionalidad()).isEqualTo("Colombia");
        assertThat(arbitro.getAniosExperiencia()).isEqualTo(15);
    }

    @Test
    @DisplayName("ArbitroMapper.toResponse copia todos los campos, incluido el id")
    void arbitro_toResponse() {
        Arbitro arbitro = new Arbitro();
        arbitro.setId(2L);
        arbitro.setNombre("Wilmar Roldán");
        arbitro.setNacionalidad(null);
        arbitro.setAniosExperiencia(0);

        assertThat(arbitroMapper.toResponse(arbitro)).isEqualTo(new ArbitroResponse(2L, "Wilmar Roldán", null, 0));
    }

    @Test
    @DisplayName("ArbitroMapper.updateEntity reemplaza los datos (incluida la nacionalidad a null) y conserva el id")
    void arbitro_updateEntity() {
        Arbitro arbitro = new Arbitro();
        arbitro.setId(2L);
        arbitro.setNombre("Viejo");
        arbitro.setNacionalidad("Chile");
        arbitro.setAniosExperiencia(3);

        arbitroMapper.updateEntity(new ArbitroRequest("Nuevo", null, 4), arbitro);

        assertThat(arbitro.getId()).isEqualTo(2L);
        assertThat(arbitro.getNombre()).isEqualTo("Nuevo");
        assertThat(arbitro.getNacionalidad()).isNull();
        assertThat(arbitro.getAniosExperiencia()).isEqualTo(4);
    }

    // --------------------------------------------------------------- Entrenador

    @Test
    @DisplayName("EntrenadorMapper.toEntity copia los campos y deja el id sin asignar")
    void entrenador_toEntity() {
        Entrenador entrenador = entrenadorMapper.toEntity(new EntrenadorRequest("Alexandre Guimarães", "Brasil"));

        assertThat(entrenador.getId()).isNull();
        assertThat(entrenador.getNombre()).isEqualTo("Alexandre Guimarães");
        assertThat(entrenador.getNacionalidad()).isEqualTo("Brasil");
    }

    @Test
    @DisplayName("EntrenadorMapper.toResponse copia todos los campos, incluido el id")
    void entrenador_toResponse() {
        Entrenador entrenador = new Entrenador();
        entrenador.setId(8L);
        entrenador.setNombre("Alexandre Guimarães");
        entrenador.setNacionalidad("Brasil");

        assertThat(entrenadorMapper.toResponse(entrenador))
                .isEqualTo(new EntrenadorResponse(8L, "Alexandre Guimarães", "Brasil"));
    }

    @Test
    @DisplayName("EntrenadorMapper.updateEntity reemplaza los datos y conserva el id")
    void entrenador_updateEntity() {
        Entrenador entrenador = new Entrenador();
        entrenador.setId(8L);
        entrenador.setNombre("Viejo");
        entrenador.setNacionalidad("Chile");

        entrenadorMapper.updateEntity(new EntrenadorRequest("Nuevo", "Perú"), entrenador);

        assertThat(entrenador.getId()).isEqualTo(8L);
        assertThat(entrenador.getNombre()).isEqualTo("Nuevo");
        assertThat(entrenador.getNacionalidad()).isEqualTo("Perú");
    }

    // --------------------------------------------------------------------- Liga

    @Test
    @DisplayName("LigaMapper.toEntity copia los campos y las fechas y deja el id sin asignar")
    void liga_toEntity() {
        Liga liga = ligaMapper.toEntity(new LigaRequest("Liga BetPlay", "2026-I",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)));

        assertThat(liga.getId()).isNull();
        assertThat(liga.getNombre()).isEqualTo("Liga BetPlay");
        assertThat(liga.getTemporada()).isEqualTo("2026-I");
        assertThat(liga.getFechaInicio()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(liga.getFechaFin()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("LigaMapper.toResponse copia todos los campos, incluido el id")
    void liga_toResponse() {
        Liga liga = new Liga();
        liga.setId(1L);
        liga.setNombre("Liga BetPlay");
        liga.setTemporada("2026-I");
        liga.setFechaInicio(LocalDate.of(2026, 2, 1));
        liga.setFechaFin(LocalDate.of(2026, 6, 30));

        assertThat(ligaMapper.toResponse(liga)).isEqualTo(new LigaResponse(1L, "Liga BetPlay", "2026-I",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    @DisplayName("LigaMapper.updateEntity reemplaza los datos y conserva el id")
    void liga_updateEntity() {
        Liga liga = new Liga();
        liga.setId(1L);
        liga.setNombre("Vieja");
        liga.setTemporada("2025-I");
        liga.setFechaInicio(LocalDate.of(2025, 1, 1));
        liga.setFechaFin(LocalDate.of(2025, 12, 1));

        ligaMapper.updateEntity(new LigaRequest("Nueva", "2026-I",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)), liga);

        assertThat(liga.getId()).isEqualTo(1L);
        assertThat(liga.getNombre()).isEqualTo("Nueva");
        assertThat(liga.getTemporada()).isEqualTo("2026-I");
        assertThat(liga.getFechaInicio()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(liga.getFechaFin()).isEqualTo(LocalDate.of(2026, 6, 30));
    }
}

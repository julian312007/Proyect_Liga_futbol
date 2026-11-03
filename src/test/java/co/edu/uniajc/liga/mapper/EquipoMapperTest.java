package co.edu.uniajc.liga.mapper;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas de {@link EquipoMapper} sin Spring: se instancia la implementación que genera MapStruct. */
class EquipoMapperTest {

    private final EquipoMapper mapper = new EquipoMapperImpl();

    @Test
    @DisplayName("toEntity copia los campos simples e ignora el id y las relaciones (las resuelve el servicio)")
    void toEntity_copiaCamposSimples_ignoraIdYRelaciones() {
        Equipo equipo = mapper.toEntity(new EquipoRequest("Deportivo Cali", "Cali", 1912, 2L, 3L, 1L));

        assertThat(equipo.getId()).isNull();
        assertThat(equipo.getNombre()).isEqualTo("Deportivo Cali");
        assertThat(equipo.getCiudad()).isEqualTo("Cali");
        assertThat(equipo.getAnioFundacion()).isEqualTo(1912);
        assertThat(equipo.getLiga()).isNull();
        assertThat(equipo.getEstadioLocal()).isNull();
        assertThat(equipo.getEntrenador()).isNull();
    }

    @Test
    @DisplayName("toResponse muestra las relaciones como resumen {id, nombre}")
    void toResponse_relacionesComoResumen() {
        EquipoResponse respuesta = mapper.toResponse(equipoCompleto());

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.nombre()).isEqualTo("Deportivo Cali");
        assertThat(respuesta.ciudad()).isEqualTo("Cali");
        assertThat(respuesta.anioFundacion()).isEqualTo(1912);
        assertThat(respuesta.liga().id()).isEqualTo(1L);
        assertThat(respuesta.liga().nombre()).isEqualTo("Liga BetPlay");
        assertThat(respuesta.estadioLocal().id()).isEqualTo(2L);
        assertThat(respuesta.estadioLocal().nombre()).isEqualTo("Pascual Guerrero");
        assertThat(respuesta.entrenador().id()).isEqualTo(3L);
        assertThat(respuesta.entrenador().nombre()).isEqualTo("Alexandre Guimaraes");
    }

    @Test
    @DisplayName("toResponse con relaciones nulas no falla: el resumen es nulo")
    void toResponse_relacionesNulas_resumenNulo() {
        Equipo equipo = new Equipo();
        equipo.setId(10L);
        equipo.setNombre("Sin relaciones");

        EquipoResponse respuesta = mapper.toResponse(equipo);

        assertThat(respuesta.liga()).isNull();
        assertThat(respuesta.estadioLocal()).isNull();
        assertThat(respuesta.entrenador()).isNull();
    }

    @Test
    @DisplayName("toResponse de null devuelve null")
    void toResponse_nulo_devuelveNulo() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("updateEntity actualiza los campos simples y no toca el id ni las relaciones")
    void updateEntity_noTocaIdNiRelaciones() {
        Equipo equipo = equipoCompleto();

        mapper.updateEntity(new EquipoRequest("Cali FC", "Palmira", 1999, 99L, 98L, 97L), equipo);

        assertThat(equipo.getId()).isEqualTo(10L);
        assertThat(equipo.getNombre()).isEqualTo("Cali FC");
        assertThat(equipo.getCiudad()).isEqualTo("Palmira");
        assertThat(equipo.getAnioFundacion()).isEqualTo(1999);
        assertThat(equipo.getLiga().getId()).isEqualTo(1L);
        assertThat(equipo.getEstadioLocal().getId()).isEqualTo(2L);
        assertThat(equipo.getEntrenador().getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("toCuerpoTecnico devuelve el entrenador del equipo con su nacionalidad")
    void toCuerpoTecnico_devuelveElEntrenador() {
        CuerpoTecnicoResponse cuerpo = mapper.toCuerpoTecnico(equipoCompleto());

        assertThat(cuerpo.equipoId()).isEqualTo(10L);
        assertThat(cuerpo.equipo()).isEqualTo("Deportivo Cali");
        assertThat(cuerpo.entrenadorId()).isEqualTo(3L);
        assertThat(cuerpo.entrenador()).isEqualTo("Alexandre Guimaraes");
        assertThat(cuerpo.nacionalidad()).isEqualTo("Brasil");
    }

    @Test
    @DisplayName("toCuerpoTecnico tolera un equipo sin entrenador y un equipo nulo")
    void toCuerpoTecnico_sinEntrenadorONulo() {
        Equipo sinEntrenador = new Equipo();
        sinEntrenador.setId(10L);
        sinEntrenador.setNombre("Sin DT");

        CuerpoTecnicoResponse cuerpo = mapper.toCuerpoTecnico(sinEntrenador);

        assertThat(cuerpo.equipoId()).isEqualTo(10L);
        assertThat(cuerpo.entrenadorId()).isNull();
        assertThat(cuerpo.entrenador()).isNull();
        assertThat(cuerpo.nacionalidad()).isNull();
        assertThat(mapper.toCuerpoTecnico(null)).isNull();
    }

    private static Equipo equipoCompleto() {
        Liga liga = new Liga();
        liga.setId(1L);
        liga.setNombre("Liga BetPlay");
        Estadio estadio = new Estadio();
        estadio.setId(2L);
        estadio.setNombre("Pascual Guerrero");
        Entrenador entrenador = new Entrenador();
        entrenador.setId(3L);
        entrenador.setNombre("Alexandre Guimaraes");
        entrenador.setNacionalidad("Brasil");

        Equipo equipo = new Equipo();
        equipo.setId(10L);
        equipo.setNombre("Deportivo Cali");
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1912);
        equipo.setLiga(liga);
        equipo.setEstadioLocal(estadio);
        equipo.setEntrenador(entrenador);
        return equipo;
    }
}

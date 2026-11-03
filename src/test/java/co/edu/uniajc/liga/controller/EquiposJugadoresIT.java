package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.PosicionJugador;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import co.edu.uniajc.liga.support.BaseIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de la Fase 3 (equipos y jugadores) con la aplicación completa, PostgreSQL real
 * (Testcontainers, Flyway con V7 y V8 y {@code ddl-auto=validate}) y la cadena de seguridad real. Requiere Docker;
 * corre en {@code mvn verify}.
 *
 * <p>Cubre: creación real por HTTP con un ADMIN guardado en la base de datos, la matriz de roles, RN-02 (el
 * jugador se mueve de equipo), RN-03 y la unicidad (liga, nombre) por HTTP, las restricciones únicas de la
 * propia base de datos (saltándose al servicio) y el borrado con dependientes, incluidos los verificadores que
 * la Fase 3 aporta a los catálogos.
 *
 * <p>Los catálogos (liga, estadio, entrenador) los inserta cada prueba con los repositorios y nombres únicos
 * ({@link #unico(String)}); no se asume ninguna tabla vacía ni se comparan totales globales.
 */
class EquiposJugadoresIT extends BaseIT {

    @Autowired
    private LigaRepository ligaRepository;
    @Autowired
    private EstadioRepository estadioRepository;
    @Autowired
    private EntrenadorRepository entrenadorRepository;
    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private JugadorRepository jugadorRepository;
    @Autowired
    private VerificadoresEliminacion verificadoresEliminacion;

    // ================================================== Creación real por HTTP

    @Test
    @DisplayName("F1 y F6: un ADMIN crea un equipo y jugadores por HTTP y se consultan el equipo, el plantel y el cuerpo técnico")
    void flujoCompleto_crearEquipoYJugadores_yConsultarPlantelYCuerpoTecnico() throws Exception {
        Liga liga = nuevaLiga();
        Estadio estadio = nuevoEstadio();
        Entrenador entrenador = nuevoEntrenador();
        String nombreEquipo = unico("Equipo IT");

        // 1. Crear el equipo: 201 con Location y relaciones como resumen.
        ResultActions creado = enviar(post("/api/v1/equipos"), cuerpoEquipo(nombreEquipo, liga, estadio, entrenador, 1912))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value(nombreEquipo))
                .andExpect(jsonPath("$.anioFundacion").value(1912))
                .andExpect(jsonPath("$.liga.id").value(liga.getId()))
                .andExpect(jsonPath("$.estadioLocal.id").value(estadio.getId()))
                .andExpect(jsonPath("$.entrenador.id").value(entrenador.getId()));
        long equipoId = idDe(creado);
        assertThat(creado.andReturn().getResponse().getHeader("Location")).endsWith("/api/v1/equipos/" + equipoId);

        // 2. Se puede leer por id (con USER) y aparece en el listado paginado.
        mockMvc.perform(get("/api/v1/equipos/" + equipoId).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(equipoId))
                .andExpect(jsonPath("$.nombre").value(nombreEquipo));
        mockMvc.perform(get("/api/v1/equipos").param("size", "5").param("sort", "id,desc")
                        .header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.tamanio").value(5));

        // 3. Dos jugadores: 201 con Location.
        ResultActions jugadorNueve = enviar(post("/api/v1/jugadores"),
                cuerpoJugador("Jugador Nueve", "2000-05-17", PosicionJugador.DELANTERO, 9, equipoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dorsal").value(9))
                .andExpect(jsonPath("$.equipo.id").value(equipoId))
                .andExpect(jsonPath("$.equipo.nombre").value(nombreEquipo));
        long jugadorNueveId = idDe(jugadorNueve);
        assertThat(jugadorNueve.andReturn().getResponse().getHeader("Location"))
                .endsWith("/api/v1/jugadores/" + jugadorNueveId);
        enviar(post("/api/v1/jugadores"), cuerpoJugador("Jugador Uno", "1998-01-02", PosicionJugador.PORTERO, 1, equipoId))
                .andExpect(status().isCreated());

        // 4. El plantel, por defecto ordenado por dorsal.
        mockMvc.perform(get("/api/v1/equipos/" + equipoId + "/plantel").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].dorsal").value(1))
                .andExpect(jsonPath("$.contenido[1].dorsal").value(9));

        // 5. El filtro opcional ?equipoId= de GET /jugadores.
        mockMvc.perform(get("/api/v1/jugadores").param("equipoId", String.valueOf(equipoId))
                        .header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(get("/api/v1/jugadores/" + jugadorNueveId).header("Authorization", authAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicion").value("DELANTERO"))
                .andExpect(jsonPath("$.fechaNacimiento").value("2000-05-17"));

        // 6. El cuerpo técnico es el entrenador del equipo.
        mockMvc.perform(get("/api/v1/equipos/" + equipoId + "/cuerpo-tecnico").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipoId").value(equipoId))
                .andExpect(jsonPath("$.entrenadorId").value(entrenador.getId()))
                .andExpect(jsonPath("$.entrenador").value(entrenador.getNombre()));
    }

    @Test
    @DisplayName("Seguridad: sin token 401, USER en escritura 403, USER en lectura 200 y equipo o jugador inexistente 404")
    void matrizDeRoles_yRecursosInexistentes() throws Exception {
        Liga liga = nuevaLiga();
        String cuerpo = cuerpoEquipo(unico("Equipo IT"), liga, nuevoEstadio(), nuevoEntrenador(), 1950);

        mockMvc.perform(get("/api/v1/equipos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/equipos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/equipos").header("Authorization", authUser())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/jugadores/999999999").header("Authorization", authUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jugadores").header("Authorization", authUser()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/equipos/999999999").header("Authorization", authUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        mockMvc.perform(get("/api/v1/equipos/999999999/plantel").header("Authorization", authUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/equipos/999999999/cuerpo-tecnico").header("Authorization", authUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jugadores/999999999").header("Authorization", authUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/jugadores/999999999").header("Authorization", authAdmin()))
                .andExpect(status().isNotFound());
        // Relación inexistente al crear: 404 (no 500).
        enviar(post("/api/v1/jugadores"), cuerpoJugador("Jugador Fantasma", "2000-01-01", PosicionJugador.DEFENSA, 5, 999999999L))
                .andExpect(status().isNotFound());
    }

    // ========================================================== Reglas de negocio

    @Test
    @DisplayName("RN-03 y equipo único por liga por HTTP: entrenador de otro equipo 409 RN-03; nombre repetido 409; el propio equipo se excluye al actualizar")
    void rn03_yNombreUnicoPorLiga_porHttp() throws Exception {
        Liga liga = nuevaLiga();
        Liga otraLiga = nuevaLiga();
        Estadio estadio = nuevoEstadio();
        Entrenador entrenadorA = nuevoEntrenador();
        Entrenador entrenadorB = nuevoEntrenador();
        String nombreA = unico("Equipo A");

        long equipoA = idDe(enviar(post("/api/v1/equipos"), cuerpoEquipo(nombreA, liga, estadio, entrenadorA, 1912))
                .andExpect(status().isCreated()));

        // RN-03: el mismo entrenador en otro equipo.
        enviar(post("/api/v1/equipos"), cuerpoEquipo(unico("Equipo B"), liga, estadio, entrenadorA, 1920))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-03"));

        // Nombre repetido en la misma liga (aunque cambie el uso de mayúsculas): 409.
        enviar(post("/api/v1/equipos"), cuerpoEquipo(nombreA.toUpperCase(), liga, estadio, entrenadorB, 1920))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EQUIPO_DUPLICADO"));

        // El mismo nombre en OTRA liga sí se permite.
        enviar(post("/api/v1/equipos"), cuerpoEquipo(nombreA, otraLiga, estadio, entrenadorB, 1920))
                .andExpect(status().isCreated());

        // Al actualizar se excluye al propio equipo: conservar nombre y entrenador es válido.
        enviar(put("/api/v1/equipos/" + equipoA), cuerpoEquipo(nombreA, liga, estadio, entrenadorA, 1913))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anioFundacion").value(1913));

        // Pero no se puede quitar el entrenador a otro: B ya dirige al equipo de la otra liga.
        enviar(put("/api/v1/equipos/" + equipoA), cuerpoEquipo(nombreA, liga, estadio, entrenadorB, 1913))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-03"));
    }

    @Test
    @DisplayName("RN-02 por HTTP: cambiar equipoId en un PUT mueve al mismo jugador; si el dorsal está ocupado en el destino, 409 DORSAL_DUPLICADO")
    void rn02_putConOtroEquipo_mueveAlJugador_noLoDuplica() throws Exception {
        Liga liga = nuevaLiga();
        long equipoOrigen = crearEquipoPorHttp(liga);
        long equipoDestino = crearEquipoPorHttp(liga);

        long jugadorId = idDe(enviar(post("/api/v1/jugadores"),
                cuerpoJugador("Jugador Viajero", "1999-03-04", PosicionJugador.MEDIOCAMPISTA, 9, equipoOrigen))
                .andExpect(status().isCreated()));
        enviar(post("/api/v1/jugadores"),
                cuerpoJugador("Jugador Del Destino", "1997-07-08", PosicionJugador.DEFENSA, 9, equipoDestino))
                .andExpect(status().isCreated());

        // Dorsal 9 ocupado en el destino: 409 y el jugador sigue donde estaba.
        enviar(put("/api/v1/jugadores/" + jugadorId),
                cuerpoJugador("Jugador Viajero", "1999-03-04", PosicionJugador.MEDIOCAMPISTA, 9, equipoDestino))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DORSAL_DUPLICADO"));
        mockMvc.perform(get("/api/v1/jugadores/" + jugadorId).header("Authorization", authUser()))
                .andExpect(jsonPath("$.equipo.id").value(equipoOrigen));

        // Con un dorsal libre en el destino, el MISMO jugador cambia de equipo.
        enviar(put("/api/v1/jugadores/" + jugadorId),
                cuerpoJugador("Jugador Viajero", "1999-03-04", PosicionJugador.MEDIOCAMPISTA, 10, equipoDestino))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jugadorId))
                .andExpect(jsonPath("$.dorsal").value(10))
                .andExpect(jsonPath("$.equipo.id").value(equipoDestino));

        mockMvc.perform(get("/api/v1/equipos/" + equipoOrigen + "/plantel").header("Authorization", authUser()))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(get("/api/v1/equipos/" + equipoDestino + "/plantel").header("Authorization", authUser()))
                .andExpect(jsonPath("$.totalElementos").value(2));
        // Se movió la fila, no se creó otra: el id sigue existiendo una sola vez y apunta al destino.
        assertThat(jugadorRepository.existsByEquipoId(equipoOrigen)).isFalse();
        assertThat(jugadorRepository.existsByEquipoIdAndDorsal(equipoDestino, 10)).isTrue();
    }

    @Test
    @DisplayName("Validaciones por HTTP: dorsal fuera de 1 a 99 es 400; fecha de nacimiento futura y año de fundación futuro son 422")
    void validaciones_dorsalFecha_yAnioFundacion() throws Exception {
        Liga liga = nuevaLiga();
        long equipoId = crearEquipoPorHttp(liga);

        enviar(post("/api/v1/jugadores"), cuerpoJugador("Jugador Cien", "2000-01-01", PosicionJugador.DEFENSA, 100, equipoId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        enviar(post("/api/v1/jugadores"),
                cuerpoJugador("Jugador Del Futuro", LocalDate.now().plusDays(1).toString(), PosicionJugador.DEFENSA, 7, equipoId))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("FECHA_NACIMIENTO_FUTURA"));
        enviar(post("/api/v1/equipos"),
                cuerpoEquipo(unico("Equipo Futuro"), liga, nuevoEstadio(), nuevoEntrenador(), Year.now().getValue() + 1))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("ANIO_FUNDACION_FUTURO"));
    }

    // ================================================ Restricciones de la base de datos

    @Test
    @DisplayName("Unicidad de la base de datos: (liga, nombre) del equipo, entrenador único y (equipo, dorsal) del jugador rechazan el duplicado aunque se salte el servicio")
    void baseDeDatos_restriccionesUnicas_rechazanDuplicados() {
        Liga liga = nuevaLiga();
        Estadio estadio = nuevoEstadio();
        Entrenador entrenadorA = nuevoEntrenador();
        Entrenador entrenadorB = nuevoEntrenador();
        Equipo equipo = equipoRepository.saveAndFlush(nuevoEquipo(unico("Equipo BD"), liga, estadio, entrenadorA));

        // uq_equipo_liga_nombre: mismo nombre exacto en la misma liga, con otro entrenador.
        assertThatThrownBy(() -> equipoRepository.saveAndFlush(
                nuevoEquipo(equipo.getNombre(), liga, estadio, entrenadorB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // uq_equipo_entrenador (RN-03): el mismo entrenador en otro equipo.
        assertThatThrownBy(() -> equipoRepository.saveAndFlush(
                nuevoEquipo(unico("Equipo BD 2"), liga, estadio, entrenadorA)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // uq_jugador_equipo_dorsal: mismo dorsal en el mismo equipo.
        jugadorRepository.saveAndFlush(nuevoJugador("Jugador BD Uno", 9, equipo));
        assertThatThrownBy(() -> jugadorRepository.saveAndFlush(nuevoJugador("Jugador BD Dos", 9, equipo)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // ck_jugador_dorsal: el CHECK de 1 a 99 también protege ante un dato que no pasó por el servicio.
        assertThatThrownBy(() -> jugadorRepository.saveAndFlush(nuevoJugador("Jugador BD Cien", 100, equipo)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ======================================================= Borrado con dependientes

    @Test
    @DisplayName("Borrado: un equipo con jugadores es 409 CONFLICTO_DEPENDENCIAS; sin jugadores se elimina (204) y luego es 404")
    void borrado_equipoConJugadores_409_yLuegoSeElimina() throws Exception {
        Liga liga = nuevaLiga();
        long equipoId = crearEquipoPorHttp(liga);
        long jugadorId = idDe(enviar(post("/api/v1/jugadores"),
                cuerpoJugador("Jugador Borrable", "2001-02-03", PosicionJugador.PORTERO, 1, equipoId))
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/v1/equipos/" + equipoId).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DEPENDENCIAS"));

        mockMvc.perform(delete("/api/v1/jugadores/" + jugadorId).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/jugadores/" + jugadorId).header("Authorization", authUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/equipos/" + equipoId).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/equipos/" + equipoId).header("Authorization", authUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EST-02 y dependencias: los verificadores de la Fase 3 vetan borrar el estadio, el entrenador y la liga de un equipo, y dejan de vetar al borrarlo")
    void verificadores_estadioEntrenadorYLigaConEquipo_lanzanConflicto() throws Exception {
        Liga liga = nuevaLiga();
        Estadio estadio = nuevoEstadio();
        Entrenador entrenador = nuevoEntrenador();
        long equipoId = idDe(enviar(post("/api/v1/equipos"),
                cuerpoEquipo(unico("Equipo Verificado"), liga, estadio, entrenador, 1930))
                .andExpect(status().isCreated()));

        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Estadio.class, estadio.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Entrenador.class, entrenador.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Liga.class, liga.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));

        mockMvc.perform(delete("/api/v1/equipos/" + equipoId).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());

        assertThatCode(() -> {
            verificadoresEliminacion.verificar(Estadio.class, estadio.getId());
            verificadoresEliminacion.verificar(Entrenador.class, entrenador.getId());
            verificadoresEliminacion.verificar(Liga.class, liga.getId());
        }).doesNotThrowAnyException();
    }

    // ================================================================== Apoyo

    private Liga nuevaLiga() {
        Liga liga = new Liga();
        liga.setNombre(unico("Liga IT"));
        liga.setTemporada("2026");
        liga.setFechaInicio(LocalDate.of(2026, 1, 1));
        liga.setFechaFin(LocalDate.of(2026, 12, 31));
        return ligaRepository.save(liga);
    }

    private Estadio nuevoEstadio() {
        Estadio estadio = new Estadio();
        estadio.setNombre(unico("Estadio IT"));
        estadio.setCiudad("Cali");
        estadio.setCapacidad(10_000);
        return estadioRepository.save(estadio);
    }

    private Entrenador nuevoEntrenador() {
        Entrenador entrenador = new Entrenador();
        entrenador.setNombre(unico("Entrenador IT"));
        entrenador.setNacionalidad("Colombia");
        return entrenadorRepository.save(entrenador);
    }

    private static Equipo nuevoEquipo(String nombre, Liga liga, Estadio estadio, Entrenador entrenador) {
        Equipo equipo = new Equipo();
        equipo.setNombre(nombre);
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1912);
        equipo.setLiga(liga);
        equipo.setEstadioLocal(estadio);
        equipo.setEntrenador(entrenador);
        return equipo;
    }

    private static Jugador nuevoJugador(String nombre, int dorsal, Equipo equipo) {
        Jugador jugador = new Jugador();
        jugador.setNombre(nombre);
        jugador.setFechaNacimiento(LocalDate.of(2000, 1, 1));
        jugador.setPosicion(PosicionJugador.DEFENSA);
        jugador.setDorsal(dorsal);
        jugador.setEquipo(equipo);
        return jugador;
    }

    /** Crea por HTTP un equipo nuevo en la liga, con estadio y entrenador propios, y devuelve su id. */
    private long crearEquipoPorHttp(Liga liga) throws Exception {
        return idDe(enviar(post("/api/v1/equipos"),
                cuerpoEquipo(unico("Equipo IT"), liga, nuevoEstadio(), nuevoEntrenador(), 1912))
                .andExpect(status().isCreated()));
    }

    /** Envía el cuerpo JSON con el token de un ADMIN real. */
    private ResultActions enviar(MockHttpServletRequestBuilder solicitud, String json) throws Exception {
        return mockMvc.perform(solicitud
                .header("Authorization", authAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static long idDe(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Number>read(cuerpo, "$.id").longValue();
    }

    private static String cuerpoEquipo(String nombre, Liga liga, Estadio estadio, Entrenador entrenador,
                                       int anioFundacion) {
        return """
                {"nombre": "%s", "ciudad": "Cali", "anioFundacion": %d,
                 "estadioLocalId": %d, "entrenadorId": %d, "ligaId": %d}
                """.formatted(nombre, anioFundacion, estadio.getId(), entrenador.getId(), liga.getId());
    }

    private static String cuerpoJugador(String nombre, String fechaNacimiento, PosicionJugador posicion, int dorsal,
                                        long equipoId) {
        return """
                {"nombre": "%s", "fechaNacimiento": "%s", "posicion": "%s", "dorsal": %d, "equipoId": %d}
                """.formatted(nombre, fechaNacimiento, posicion.name(), dorsal, equipoId);
    }
}

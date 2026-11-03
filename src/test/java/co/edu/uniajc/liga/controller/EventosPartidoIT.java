package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Gol;
import co.edu.uniajc.liga.entity.Jugador;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.entity.PosicionJugador;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.service.SuspensionService;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import co.edu.uniajc.liga.support.BaseIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de la Fase 5 (F3; RN-08 a RN-12) con la aplicación completa, PostgreSQL real (Flyway con las
 * migraciones V10 y V11 y {@code ddl-auto=validate}) y la seguridad real. Requiere Docker.
 *
 * <p>Los catálogos, equipos, jugadores y partidos se insertan con los repositorios base (la Fase 4 todavía
 * no se usa aquí) y con nombres únicos; el partido se pone {@code EN_JUEGO} directamente en la base, en lugar
 * de llamar a {@code POST /iniciar}. Los goles, tarjetas y el cierre sí se ejercitan por HTTP con un ADMIN
 * real.
 */
class EventosPartidoIT extends BaseIT {

    private static final Instant FECHA_BASE = Instant.parse("2026-03-07T20:00:00Z");

    @Autowired
    private LigaRepository ligaRepository;
    @Autowired
    private EstadioRepository estadioRepository;
    @Autowired
    private EntrenadorRepository entrenadorRepository;
    @Autowired
    private ArbitroRepository arbitroRepository;
    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private JugadorRepository jugadorRepository;
    @Autowired
    private PartidoRepository partidoRepository;
    @Autowired
    private GolRepository golRepository;
    @Autowired
    private TarjetaRepository tarjetaRepository;
    @Autowired
    private SuspensionService suspensionService;
    @Autowired
    private VerificadoresEliminacion verificadoresEliminacion;

    // ======================================================================= Pruebas

    @Test
    @DisplayName("Flujo completo: goles y tarjetas (con RN-09 y RN-08) -> finalizar (RN-11) -> todo rechazado por RN-12")
    void flujoCompleto_golesTarjetasFinalizarYRn12() throws Exception {
        Escenario e = crearEscenario();
        Partido partido = crearPartidoEnJuego(e, 1, FECHA_BASE);
        Long id = partido.getId();
        String admin = authAdmin();

        // El partido empieza sin eventos.
        mockMvc.perform(get(rutaGoles(id)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));

        // Goles: L1 normal (local 1), V1 normal (visitante 1) y V1 autogol (suma al local: local 2).
        postGol(admin, id, e.l1().getId(), 10, "NORMAL")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(rutaGoles(id) + "/")))
                .andExpect(jsonPath("$.partidoId").value(id.intValue()))
                .andExpect(jsonPath("$.jugador.id").value(e.l1().getId().intValue()))
                .andExpect(jsonPath("$.equipo.id").value(e.local().getId().intValue()));
        postGol(admin, id, e.v1().getId(), 30, "NORMAL").andExpect(status().isCreated());
        postGol(admin, id, e.v1().getId(), 50, "AUTOGOL").andExpect(status().isCreated());

        // RN-09: la segunda amarilla de L2 crea la roja en el mismo minuto.
        postTarjeta(admin, id, e.l2().getId(), "AMARILLA", 20).andExpect(status().isCreated());
        postTarjeta(admin, id, e.l2().getId(), "AMARILLA", 40)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(rutaTarjetas(id) + "/")));
        String tarjetas = mockMvc.perform(get(rutaTarjetas(id)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> tipos = JsonPath.read(tarjetas, "$.contenido[*].tipo");
        List<Integer> minutos = JsonPath.read(tarjetas, "$.contenido[*].minuto");
        assertThat(tipos).containsExactly("AMARILLA", "AMARILLA", "ROJA");
        assertThat(minutos).containsExactly(20, 40, 40);

        // L2 ya está expulsado: no se le admiten más tarjetas (422).
        postTarjeta(admin, id, e.l2().getId(), "AMARILLA", 60)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("TARJETA_JUGADOR_EXPULSADO"));

        // RN-08: un jugador de un tercer equipo no puede marcar ni recibir tarjetas (422).
        postGol(admin, id, e.x1().getId(), 70, "NORMAL")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-08"));
        postTarjeta(admin, id, e.x1().getId(), "AMARILLA", 70)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-08"));

        // RN-11: con los goles registrados el marcador es 2-1; 3-1 se rechaza y el partido sigue EN_JUEGO.
        finalizar(admin, id, 3, 1)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-11"));
        assertThat(partidoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPartido.EN_JUEGO);

        finalizar(admin, id, 2, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidoId").value(id.intValue()))
                .andExpect(jsonPath("$.ligaId").value(e.liga().getId().intValue()))
                .andExpect(jsonPath("$.golesLocal").value(2))
                .andExpect(jsonPath("$.golesVisitante").value(1))
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));
        Partido guardado = partidoRepository.findById(id).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoPartido.FINALIZADO);
        assertThat(guardado.getGolesLocal()).isEqualTo(2);
        assertThat(guardado.getGolesVisitante()).isEqualTo(1);

        // RN-12: un partido finalizado no admite más goles, tarjetas, borrados ni otra finalización (409).
        postGol(admin, id, e.l1().getId(), 80, "NORMAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        postTarjeta(admin, id, e.v1().getId(), "AMARILLA", 85)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        Long golId = golRepository.findByPartidoId(id, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();
        mockMvc.perform(delete(rutaGoles(id) + "/" + golId).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        finalizar(admin, id, 2, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        assertThat(golRepository.findByPartidoId(id, org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("RN-10: quien vio una roja en el partido anterior de su equipo no puede tener eventos en el siguiente")
    void rn10_suspensionEntrePartidos() throws Exception {
        Escenario e = crearEscenario();
        Partido anterior = crearPartidoEnJuego(e, 1, FECHA_BASE);
        Partido siguiente = crearPartidoEnJuego(e, 2, FECHA_BASE.plus(7, ChronoUnit.DAYS));
        String admin = authAdmin();

        // Roja directa de L2 en el primer partido, que luego se finaliza 0-0.
        postTarjeta(admin, anterior.getId(), e.l2().getId(), "ROJA", 80).andExpect(status().isCreated());
        finalizar(admin, anterior.getId(), 0, 0).andExpect(status().isOk());

        // En el partido siguiente, L2 está suspendido; L1 no.
        postGol(admin, siguiente.getId(), e.l2().getId(), 15, "NORMAL")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-10"));
        postTarjeta(admin, siguiente.getId(), e.l2().getId(), "AMARILLA", 15)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-10"));
        postGol(admin, siguiente.getId(), e.l1().getId(), 15, "NORMAL").andExpect(status().isCreated());

        // SuspensionService (la usa también la Fase 6): ve lo mismo que el servicio de goles.
        assertThat(suspensionService.estaSuspendido(e.l2().getId(), siguiente.getId())).isTrue();
        assertThat(suspensionService.estaSuspendido(e.l1().getId(), siguiente.getId())).isFalse();
        assertThat(suspensionService.estaSuspendido(e.l2().getId(), anterior.getId())).isFalse();
        List<SuspendidoResponse> suspendidos = suspensionService.suspendidosEnPartido(siguiente.getId());
        assertThat(suspendidos).hasSize(1);
        assertThat(suspendidos.get(0).jugadorId()).isEqualTo(e.l2().getId());
        assertThat(suspendidos.get(0).equipoId()).isEqualTo(e.local().getId());
        assertThat(suspendidos.get(0).partidoRojaId()).isEqualTo(anterior.getId());
        assertThat(suspendidos.get(0).jornadaRoja()).isEqualTo(1);
    }

    @Test
    @DisplayName("Borrado con dependientes y última defensa de la base de datos")
    void dependientesYRestriccionesDeLaBaseDeDatos() throws Exception {
        Escenario e = crearEscenario();
        Partido partido = crearPartidoEnJuego(e, 1, FECHA_BASE);
        String admin = authAdmin();
        postGol(admin, partido.getId(), e.l1().getId(), 10, "NORMAL").andExpect(status().isCreated());

        // Un jugador con goles no se puede eliminar (409); uno sin eventos sí.
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Jugador.class, e.l1().getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
        assertThatCode(() -> verificadoresEliminacion.verificar(Jugador.class, e.x1().getId()))
                .doesNotThrowAnyException();

        // Un jugador con tarjetas tampoco.
        postTarjeta(admin, partido.getId(), e.v1().getId(), "AMARILLA", 33).andExpect(status().isCreated());
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Jugador.class, e.v1().getId()))
                .isInstanceOf(ConflictoException.class);

        // La base de datos también defiende el rango del minuto aunque se salte el servicio (CHECK 1-130).
        Gol golInvalido = new Gol();
        golInvalido.setPartido(partido);
        golInvalido.setJugador(e.l1());
        golInvalido.setMinuto(0);
        golInvalido.setTipo(TipoGol.NORMAL);
        assertThatThrownBy(() -> golRepository.saveAndFlush(golInvalido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Matriz de roles con usuarios reales: sin token 401, USER 403 en escritura y 200 en lectura")
    void matrizDeRoles() throws Exception {
        Escenario e = crearEscenario();
        Partido partido = crearPartidoEnJuego(e, 1, FECHA_BASE);
        Long id = partido.getId();
        String usuario = authUser();

        mockMvc.perform(get(rutaGoles(id))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(rutaTarjetas(id))).andExpect(status().isUnauthorized());
        postGol(null, id, e.l1().getId(), 10, "NORMAL").andExpect(status().isUnauthorized());

        postGol(usuario, id, e.l1().getId(), 10, "NORMAL").andExpect(status().isForbidden());
        postTarjeta(usuario, id, e.l1().getId(), "AMARILLA", 10).andExpect(status().isForbidden());
        finalizar(usuario, id, 0, 0).andExpect(status().isForbidden());
        mockMvc.perform(delete(rutaGoles(id) + "/1").header("Authorization", usuario))
                .andExpect(status().isForbidden());
        assertThat(golRepository.findByPartidoId(id, org.springframework.data.domain.PageRequest.of(0, 5))
                .getTotalElements()).isZero();

        mockMvc.perform(get(rutaGoles(id)).header("Authorization", usuario)).andExpect(status().isOk());
        mockMvc.perform(get(rutaTarjetas(id)).header("Authorization", usuario)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un partido inexistente da 404 en goles, tarjetas y finalizar")
    void partidoInexistente_devuelve404() throws Exception {
        String admin = authAdmin();
        Long inexistente = 987_654_321L;

        mockMvc.perform(get(rutaGoles(inexistente)).header("Authorization", admin)).andExpect(status().isNotFound());
        postGol(admin, inexistente, 1L, 10, "NORMAL").andExpect(status().isNotFound());
        postTarjeta(admin, inexistente, 1L, "AMARILLA", 10).andExpect(status().isNotFound());
        finalizar(admin, inexistente, 0, 0).andExpect(status().isNotFound());
    }

    // ======================================================================== Apoyo

    private static String rutaGoles(Long partidoId) {
        return "/api/v1/partidos/" + partidoId + "/goles";
    }

    private static String rutaTarjetas(Long partidoId) {
        return "/api/v1/partidos/" + partidoId + "/tarjetas";
    }

    /** {@code auth} nulo = sin cabecera Authorization. */
    private ResultActions postGol(String auth, Long partidoId, Long jugadorId, int minuto, String tipo)
            throws Exception {
        var solicitud = post(rutaGoles(partidoId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jugadorId\": %d, \"minuto\": %d, \"tipo\": \"%s\"}".formatted(jugadorId, minuto, tipo));
        if (auth != null) {
            solicitud.header("Authorization", auth);
        }
        return mockMvc.perform(solicitud);
    }

    private ResultActions postTarjeta(String auth, Long partidoId, Long jugadorId, String tipo, int minuto)
            throws Exception {
        var solicitud = post(rutaTarjetas(partidoId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jugadorId\": %d, \"tipo\": \"%s\", \"minuto\": %d}".formatted(jugadorId, tipo, minuto));
        if (auth != null) {
            solicitud.header("Authorization", auth);
        }
        return mockMvc.perform(solicitud);
    }

    private ResultActions finalizar(String auth, Long partidoId, int golesLocal, int golesVisitante) throws Exception {
        return mockMvc.perform(post("/api/v1/partidos/" + partidoId + "/finalizar")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"golesLocal\": %d, \"golesVisitante\": %d}".formatted(golesLocal, golesVisitante)));
    }

    /** Datos de un escenario: una liga, tres equipos y cuatro jugadores (L1 y L2 locales, V1 visitante, X1 de otro equipo). */
    private record Escenario(Liga liga, Estadio estadio, Arbitro arbitro, Equipo local, Equipo visitante,
                             Jugador l1, Jugador l2, Jugador v1, Jugador x1) {
    }

    private Escenario crearEscenario() {
        Liga liga = new Liga();
        liga.setNombre(unico("Liga IT eventos"));
        liga.setTemporada("2026");
        liga.setFechaInicio(LocalDate.of(2026, 1, 1));
        liga.setFechaFin(LocalDate.of(2026, 12, 31));
        liga = ligaRepository.save(liga);

        Estadio estadio = new Estadio();
        estadio.setNombre(unico("Estadio IT"));
        estadio.setCiudad("Cali");
        estadio.setCapacidad(30000);
        estadio = estadioRepository.save(estadio);

        Arbitro arbitro = new Arbitro();
        arbitro.setNombre(unico("Arbitro IT"));
        arbitro.setNacionalidad("Colombia");
        arbitro.setAniosExperiencia(5);
        arbitro = arbitroRepository.save(arbitro);

        Equipo local = crearEquipo(liga, estadio, "Local IT");
        Equipo visitante = crearEquipo(liga, estadio, "Visitante IT");
        Equipo ajeno = crearEquipo(liga, estadio, "Ajeno IT");

        return new Escenario(liga, estadio, arbitro, local, visitante,
                crearJugador(local, "Luis Local", 9),
                crearJugador(local, "Lucas Local", 10),
                crearJugador(visitante, "Victor Visitante", 9),
                crearJugador(ajeno, "Andres Ajeno", 9));
    }

    private Equipo crearEquipo(Liga liga, Estadio estadio, String base) {
        Entrenador entrenador = new Entrenador();
        entrenador.setNombre(unico("Entrenador " + base));
        entrenador.setNacionalidad("Colombia");
        entrenador = entrenadorRepository.save(entrenador);

        Equipo equipo = new Equipo();
        equipo.setNombre(unico(base));
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1990);
        equipo.setEstadioLocal(estadio);
        equipo.setEntrenador(entrenador);
        equipo.setLiga(liga);
        return equipoRepository.save(equipo);
    }

    private Jugador crearJugador(Equipo equipo, String nombre, int dorsal) {
        Jugador jugador = new Jugador();
        jugador.setNombre(unico(nombre));
        jugador.setFechaNacimiento(LocalDate.of(1998, 5, 20));
        jugador.setPosicion(PosicionJugador.DELANTERO);
        jugador.setDorsal(dorsal);
        jugador.setEquipo(equipo);
        return jugadorRepository.save(jugador);
    }

    /** Inserta el partido ya {@code EN_JUEGO} (lo que dejaría {@code POST /iniciar}) en la fecha dada. */
    private Partido crearPartidoEnJuego(Escenario e, int jornada, Instant fechaHora) {
        Partido partido = new Partido();
        partido.setLiga(e.liga());
        partido.setJornada(jornada);
        partido.setFechaHora(fechaHora);
        partido.setEquipoLocal(e.local());
        partido.setEquipoVisitante(e.visitante());
        partido.setEstadio(e.estadio());
        partido.setArbitro(e.arbitro());
        partido.setEstado(EstadoPartido.EN_JUEGO);
        return partidoRepository.save(partido);
    }
}

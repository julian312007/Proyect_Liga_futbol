package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.config.NombresCache;
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
import co.edu.uniajc.liga.entity.Tarjeta;
import co.edu.uniajc.liga.entity.TipoGol;
import co.edu.uniajc.liga.entity.TipoTarjeta;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.GolRepository;
import co.edu.uniajc.liga.repository.JugadorRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.repository.TarjetaRepository;
import co.edu.uniajc.liga.support.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de la Fase 6 (requiere Docker; corre en {@code mvn verify}): tabla de posiciones, goleadores,
 * sanciones, suspendidos e historial sobre una liga propia, con PostgreSQL real y los permisos reales.
 *
 * <p>Los datos se insertan con los repositorios base, con nombres únicos, sin asumir tablas vacías: cada prueba
 * arma su propia liga. El perfil de pruebas usa {@code spring.cache.type=simple}, así que la caché es en
 * memoria; lo que se comprueba es que las anotaciones de los servicios guardan en la caché con el id de la liga.
 *
 * <p>Escenario (3 equipos; el marcador de cada partido finalizado coincide con sus goles):
 * <pre>
 *   P1 jornada 1  A 2-0 B  FINALIZADO  goles: Ana (A) normal y penal | tarjetas: Bruno (B) 2 amarillas + roja, Ana 1 amarilla
 *   P2 jornada 2  B 1-1 C  FINALIZADO  goles: Beto (B) normal | Bruno (B) autogol, que suma a C
 *   P3 jornada 3  A vs C   EN_JUEGO    gol de Carla (C), que no cuenta porque el partido no terminó
 *
 *   Tabla: A 3 pts (DG +2) | C 1 pt (DG 0) | B 1 pt (DG -2)
 *   Goleadores: Ana 2, Beto 1 (el autogol de Bruno y el gol del partido en juego no suman)
 *   Sanciones: Bruno 2 amarillas y 1 roja, Ana 1 amarilla
 *   Suspendidos de la jornada 2: Bruno (roja en el partido anterior de su equipo, P1)
 * </pre>
 *
 * <p>Requiere el bean {@code SuspensionService} de la Fase 5 en el contexto.
 */
class EstadisticasIT extends BaseIT {

    private static final Instant FECHA_P1 = Instant.parse("2026-03-01T20:00:00Z");
    private static final Instant FECHA_P2 = Instant.parse("2026-03-08T20:00:00Z");
    private static final Instant FECHA_P3 = Instant.parse("2026-03-15T20:00:00Z");

    @Autowired
    private LigaRepository ligaRepository;
    @Autowired
    private EstadioRepository estadioRepository;
    @Autowired
    private ArbitroRepository arbitroRepository;
    @Autowired
    private EntrenadorRepository entrenadorRepository;
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
    private CacheManager cacheManager;

    // ============================================================ Tabla de posiciones

    @Test
    @DisplayName("RN-01: la tabla de posiciones de una liga propia suma 3/1/0 solo con partidos finalizados y ordena por puntos, DG y GF")
    void tablaDePosiciones_calculaYOrdenaConPartidosFinalizados() throws Exception {
        Escenario e = crearEscenario();

        mockMvc.perform(get("/api/v1/ligas/{id}/tabla-posiciones", e.ligaId()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // A: ganó 2-0 (el partido en juego no cuenta)
                .andExpect(jsonPath("$[0].posicion").value(1))
                .andExpect(jsonPath("$[0].equipoId").value(e.equipoA()))
                .andExpect(jsonPath("$[0].pj").value(1))
                .andExpect(jsonPath("$[0].pg").value(1))
                .andExpect(jsonPath("$[0].gf").value(2))
                .andExpect(jsonPath("$[0].gc").value(0))
                .andExpect(jsonPath("$[0].dg").value(2))
                .andExpect(jsonPath("$[0].pts").value(3))
                // C: empató 1-1; mismos puntos que B pero mejor diferencia de gol
                .andExpect(jsonPath("$[1].posicion").value(2))
                .andExpect(jsonPath("$[1].equipoId").value(e.equipoC()))
                .andExpect(jsonPath("$[1].pj").value(1))
                .andExpect(jsonPath("$[1].pe").value(1))
                .andExpect(jsonPath("$[1].dg").value(0))
                .andExpect(jsonPath("$[1].pts").value(1))
                // B: perdió 0-2 y empató 1-1
                .andExpect(jsonPath("$[2].posicion").value(3))
                .andExpect(jsonPath("$[2].equipoId").value(e.equipoB()))
                .andExpect(jsonPath("$[2].pj").value(2))
                .andExpect(jsonPath("$[2].pe").value(1))
                .andExpect(jsonPath("$[2].pp").value(1))
                .andExpect(jsonPath("$[2].gf").value(1))
                .andExpect(jsonPath("$[2].gc").value(3))
                .andExpect(jsonPath("$[2].dg").value(-2))
                .andExpect(jsonPath("$[2].pts").value(1));
    }

    @Test
    @DisplayName("D-3: la consulta queda en la caché con el id de la liga como clave y la 2.ª respuesta es idéntica")
    void tablaDePosiciones_quedaEnLaCache() throws Exception {
        Escenario e = crearEscenario();
        String ruta = "/api/v1/ligas/" + e.ligaId() + "/tabla-posiciones";

        String primera = mockMvc.perform(get(ruta).header("Authorization", authUser()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String segunda = mockMvc.perform(get(ruta).header("Authorization", authAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primera);
        assertThat(cacheManager.getCache(NombresCache.TABLA_POSICIONES).get(e.ligaId())).isNotNull();
    }

    @Test
    @DisplayName("Una liga sin equipos devuelve la tabla vacía; una liga inexistente, 404")
    void tablaDePosiciones_ligaVaciaYLigaInexistente() throws Exception {
        Liga vacia = crearLiga();

        mockMvc.perform(get("/api/v1/ligas/{id}/tabla-posiciones", vacia.getId()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/ligas/{id}/tabla-posiciones", Long.MAX_VALUE).header("Authorization", authUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ================================================================== Goleadores

    @Test
    @DisplayName("Goleadores: suman NORMAL y PENAL de partidos finalizados; el AUTOGOL y los goles de un partido en juego no")
    void goleadores_sumanNormalYPenalDePartidosFinalizados() throws Exception {
        Escenario e = crearEscenario();

        mockMvc.perform(get("/api/v1/ligas/{id}/goleadores", e.ligaId()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].posicion").value(1))
                .andExpect(jsonPath("$[0].jugadorId").value(e.jugadorAna()))
                .andExpect(jsonPath("$[0].equipoId").value(e.equipoA()))
                .andExpect(jsonPath("$[0].goles").value(2))
                .andExpect(jsonPath("$[1].jugadorId").value(e.jugadorBeto()))
                .andExpect(jsonPath("$[1].goles").value(1));

        assertThat(cacheManager.getCache(NombresCache.GOLEADORES).get(e.ligaId())).isNotNull();
    }

    // =================================================================== Sanciones

    @Test
    @DisplayName("Sanciones: cuenta amarillas y rojas por jugador, con más rojas primero")
    void sanciones_cuentaAmarillasYRojas() throws Exception {
        Escenario e = crearEscenario();

        mockMvc.perform(get("/api/v1/ligas/{id}/sanciones", e.ligaId()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jugadorId").value(e.jugadorBruno()))
                .andExpect(jsonPath("$[0].amarillas").value(2))
                .andExpect(jsonPath("$[0].rojas").value(1))
                .andExpect(jsonPath("$[1].jugadorId").value(e.jugadorAna()))
                .andExpect(jsonPath("$[1].amarillas").value(1))
                .andExpect(jsonPath("$[1].rojas").value(0));
    }

    @Test
    @DisplayName("RN-10: el expulsado en el partido anterior de su equipo figura como suspendido en la jornada siguiente")
    void suspendidos_elExpulsadoFiguraEnLaJornadaSiguiente() throws Exception {
        Escenario e = crearEscenario();

        mockMvc.perform(get("/api/v1/ligas/{id}/sanciones/suspendidos", e.ligaId())
                        .param("jornada", "2").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jugadorId").value(e.jugadorBruno()))
                .andExpect(jsonPath("$[0].equipoId").value(e.equipoB()))
                .andExpect(jsonPath("$[0].partidoRojaId").value(e.partido1()))
                .andExpect(jsonPath("$[0].jornadaRoja").value(1));

        // La jornada 1 no tiene partido anterior: nadie está suspendido.
        mockMvc.perform(get("/api/v1/ligas/{id}/sanciones/suspendidos", e.ligaId())
                        .param("jornada", "1").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ================================================================= Historial

    @Test
    @DisplayName("Historial: partidos del equipo, el más reciente primero, con el resultado visto desde ese equipo")
    void historial_devuelveLosPartidosDelEquipoPaginados() throws Exception {
        Escenario e = crearEscenario();

        // B jugó P1 (perdió 0-2 de visitante) y P2 (empató 1-1 de local).
        mockMvc.perform(get("/api/v1/equipos/{id}/partidos", e.equipoB()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(e.partido2()))
                .andExpect(jsonPath("$.contenido[0].esLocal").value(true))
                .andExpect(jsonPath("$.contenido[0].resultado").value("EMPATE"))
                .andExpect(jsonPath("$.contenido[1].id").value(e.partido1()))
                .andExpect(jsonPath("$.contenido[1].esLocal").value(false))
                .andExpect(jsonPath("$.contenido[1].resultado").value("DERROTA"))
                .andExpect(jsonPath("$.contenido[1].liga.id").value(e.ligaId()));

        // A jugó P1 (ganó) y P3 (en juego, sin resultado); segunda página de tamaño 1.
        mockMvc.perform(get("/api/v1/equipos/{id}/partidos", e.equipoA())
                        .param("size", "1").param("page", "1").header("Authorization", authAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(e.partido1()))
                .andExpect(jsonPath("$.contenido[0].resultado").value("VICTORIA"));
    }

    @Test
    @DisplayName("Historial: ordenar por una propiedad inexistente no da 500 (se usa el orden por defecto) y un equipo inexistente da 404")
    void historial_ordenInvalidoYEquipoInexistente() throws Exception {
        Escenario e = crearEscenario();

        mockMvc.perform(get("/api/v1/equipos/{id}/partidos", e.equipoA())
                        .param("sort", "noExiste,asc").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(e.partido3()));

        mockMvc.perform(get("/api/v1/equipos/{id}/partidos", Long.MAX_VALUE).header("Authorization", authUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // =================================================================== Seguridad

    @Test
    @DisplayName("Sin token las estadísticas dan 401, también la de una liga que no existe")
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/ligas/1/tabla-posiciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ligas/1/goleadores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ligas/1/sanciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/equipos/1/partidos")).andExpect(status().isUnauthorized());
    }

    // ====================================================== Datos de la prueba

    /** Ids de lo que crea {@link #crearEscenario()}. */
    private record Escenario(Long ligaId, Long equipoA, Long equipoB, Long equipoC,
                             Long jugadorAna, Long jugadorBeto, Long jugadorBruno,
                             Long partido1, Long partido2, Long partido3) {
    }

    private Escenario crearEscenario() {
        Liga liga = crearLiga();
        Estadio estadio = crearEstadio();
        Arbitro arbitro = crearArbitro();
        Equipo a = crearEquipo(liga, estadio, "Equipo A");
        Equipo b = crearEquipo(liga, estadio, "Equipo B");
        Equipo c = crearEquipo(liga, estadio, "Equipo C");
        Jugador ana = crearJugador(a, "Ana", 9);
        Jugador beto = crearJugador(b, "Beto", 9);
        Jugador bruno = crearJugador(b, "Bruno", 4);
        Jugador carla = crearJugador(c, "Carla", 9);

        Partido p1 = crearPartido(liga, 1, FECHA_P1, a, b, estadio, arbitro, 2, 0, EstadoPartido.FINALIZADO);
        Partido p2 = crearPartido(liga, 2, FECHA_P2, b, c, estadio, arbitro, 1, 1, EstadoPartido.FINALIZADO);
        Partido p3 = crearPartido(liga, 3, FECHA_P3, a, c, estadio, arbitro, 0, 0, EstadoPartido.EN_JUEGO);

        crearGol(p1, ana, 10, TipoGol.NORMAL);
        crearGol(p1, ana, 70, TipoGol.PENAL);
        crearGol(p2, beto, 15, TipoGol.NORMAL);
        crearGol(p2, bruno, 80, TipoGol.AUTOGOL);
        crearGol(p3, carla, 5, TipoGol.NORMAL);

        crearTarjeta(p1, bruno, TipoTarjeta.AMARILLA, 20);
        crearTarjeta(p1, bruno, TipoTarjeta.AMARILLA, 60);
        crearTarjeta(p1, bruno, TipoTarjeta.ROJA, 60);
        crearTarjeta(p1, ana, TipoTarjeta.AMARILLA, 30);

        return new Escenario(liga.getId(), a.getId(), b.getId(), c.getId(), ana.getId(), beto.getId(), bruno.getId(),
                p1.getId(), p2.getId(), p3.getId());
    }

    private Liga crearLiga() {
        Liga liga = new Liga();
        liga.setNombre(unico("Liga IT Estadisticas"));
        liga.setTemporada("2026");
        liga.setFechaInicio(LocalDate.of(2026, 1, 1));
        liga.setFechaFin(LocalDate.of(2026, 12, 1));
        return ligaRepository.save(liga);
    }

    private Estadio crearEstadio() {
        Estadio estadio = new Estadio();
        estadio.setNombre(unico("Estadio IT"));
        estadio.setCiudad("Cali");
        estadio.setCapacidad(10_000);
        return estadioRepository.save(estadio);
    }

    private Arbitro crearArbitro() {
        Arbitro arbitro = new Arbitro();
        arbitro.setNombre(unico("Arbitro IT"));
        arbitro.setNacionalidad("Colombia");
        arbitro.setAniosExperiencia(5);
        return arbitroRepository.save(arbitro);
    }

    private Equipo crearEquipo(Liga liga, Estadio estadio, String base) {
        Entrenador entrenador = new Entrenador();
        entrenador.setNombre(unico("Entrenador IT"));
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

    private Jugador crearJugador(Equipo equipo, String base, int dorsal) {
        Jugador jugador = new Jugador();
        jugador.setNombre(unico(base));
        jugador.setFechaNacimiento(LocalDate.of(1995, 5, 5));
        jugador.setPosicion(PosicionJugador.DELANTERO);
        jugador.setDorsal(dorsal);
        jugador.setEquipo(equipo);
        return jugadorRepository.save(jugador);
    }

    private Partido crearPartido(Liga liga, int jornada, Instant fechaHora, Equipo local, Equipo visitante,
                                 Estadio estadio, Arbitro arbitro, int golesLocal, int golesVisitante,
                                 EstadoPartido estado) {
        Partido partido = new Partido();
        partido.setLiga(liga);
        partido.setJornada(jornada);
        partido.setFechaHora(fechaHora);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setEstadio(estadio);
        partido.setArbitro(arbitro);
        partido.setGolesLocal(golesLocal);
        partido.setGolesVisitante(golesVisitante);
        partido.setEstado(estado);
        return partidoRepository.save(partido);
    }

    private void crearGol(Partido partido, Jugador jugador, int minuto, TipoGol tipo) {
        Gol gol = new Gol();
        gol.setPartido(partido);
        gol.setJugador(jugador);
        gol.setMinuto(minuto);
        gol.setTipo(tipo);
        golRepository.save(gol);
    }

    private void crearTarjeta(Partido partido, Jugador jugador, TipoTarjeta tipo, int minuto) {
        Tarjeta tarjeta = new Tarjeta();
        tarjeta.setPartido(partido);
        tarjeta.setJugador(jugador);
        tarjeta.setTipo(tipo);
        tarjeta.setMinuto(minuto);
        tarjetaRepository.save(tarjeta);
    }
}

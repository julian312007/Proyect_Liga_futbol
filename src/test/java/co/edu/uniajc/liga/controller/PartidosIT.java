package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.EstadoPartido;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.entity.Partido;
import co.edu.uniajc.liga.exception.ConflictoException;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.repository.PartidoRepository;
import co.edu.uniajc.liga.service.VerificadoresEliminacion;
import co.edu.uniajc.liga.support.BaseIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de la Fase 4 (partidos y calendario) con la aplicación completa, PostgreSQL real
 * (Testcontainers, Flyway con las migraciones V3 a V9 y {@code ddl-auto=validate}) y la cadena de seguridad
 * real. Requiere Docker; corre en {@code mvn verify}.
 *
 * <p>Los catálogos y los equipos se insertan directamente con los repositorios (la Fase 4 no depende de los
 * controladores de las fases 2 y 3) con nombres únicos: no se asume que las tablas estén vacías. Cubre la
 * creación real por HTTP con un ADMIN, el calendario con filtros, cada regla (RN-04 a RN-07, RN-12), una
 * violación real de los índices únicos de la base de datos (RN-06 y RN-07) y el borrado con dependientes
 * (los verificadores de la Fase 4).
 */
class PartidosIT extends BaseIT {

    private static final String RUTA = "/api/v1/partidos";
    private static final String T1 = "2026-03-07T20:00:00-05:00";
    private static final String T2 = "2026-03-08T18:00:00-05:00";

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
    private PartidoRepository partidoRepository;
    @Autowired
    private VerificadoresEliminacion verificadoresEliminacion;

    // ===================================================== Programar por HTTP y consultar

    @Test
    @DisplayName("ADMIN programa un partido por HTTP: 201 con Location, queda PROGRAMADO 0-0 y se consulta por id")
    void programarPartido_porHttp_devuelve201YSeConsulta() throws Exception {
        Escenario e = new Escenario();

        MvcResult creado = programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.golesLocal").value(0))
                .andExpect(jsonPath("$.golesVisitante").value(0))
                .andExpect(jsonPath("$.jornada").value(1))
                .andExpect(jsonPath("$.fechaHora").value("2026-03-08T01:00:00Z"))
                .andExpect(jsonPath("$.liga.id").value(e.liga.getId()))
                .andExpect(jsonPath("$.equipoLocal.id").value(e.a.getId()))
                .andExpect(jsonPath("$.equipoVisitante.nombre").value(e.b.getNombre()))
                .andExpect(jsonPath("$.estadio.id").value(e.estadio1.getId()))
                .andExpect(jsonPath("$.arbitro.id").value(e.arbitro1.getId()))
                .andReturn();
        long id = idDe(creado);
        assertThat(creado.getResponse().getHeader("Location")).endsWith(RUTA + "/" + id);

        mockMvc.perform(get(RUTA + "/" + id).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.liga.nombre").value(e.liga.getNombre()));
    }

    @Test
    @DisplayName("Programar sin token es 401 y con USER es 403; consultar un partido inexistente es 404")
    void seguridadYNoEncontrado() throws Exception {
        Escenario e = new Escenario();
        String cuerpo = cuerpo(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(RUTA).header("Authorization", authUser())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA + "/999999999").header("Authorization", authUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Programar con una liga inexistente es 404 y con el cuerpo vacío es 400")
    void programar_relacionInexistenteOCuerpoInvalido() throws Exception {
        Escenario e = new Escenario();
        String conLigaInexistente = cuerpo(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1)
                .replace("\"ligaId\": " + e.liga.getId(), "\"ligaId\": 999999999");

        mockMvc.perform(post(RUTA).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(conLigaInexistente))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RUTA).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // ====================================================== Calendario con filtros (F8)

    @Test
    @DisplayName("Calendario: filtra por liga, jornada, equipo (local o visitante) y estadio, ordena y pagina")
    void calendario_filtraOrdenaYPagina() throws Exception {
        Escenario e = new Escenario();
        // Jornada 1: A-B en el estadio 1 y C-D en el estadio 2 (misma hora, distinto estadio y árbitro).
        long p1 = idDe(programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());
        long p2 = idDe(programar(e.liga, 1, T1, e.c, e.d, e.estadio2, e.arbitro2).andExpect(status().isCreated()).andReturn());
        // Jornada 2: B-C en el estadio 1.
        long p3 = idDe(programar(e.liga, 2, T2, e.b, e.c, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());

        String liga = "ligaId=" + e.liga.getId();

        calendario(liga).andExpect(jsonPath("$.totalElementos").value(3))
                // Orden por defecto: jornada, fecha, id.
                .andExpect(jsonPath("$.contenido[0].jornada").value(1))
                .andExpect(jsonPath("$.contenido[2].id").value(p3));
        calendario(liga + "&jornada=1").andExpect(jsonPath("$.totalElementos").value(2));
        calendario(liga + "&jornada=2").andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(p3));
        // B juega de visitante en la jornada 1 y de local en la 2.
        calendario(liga + "&equipoId=" + e.b.getId()).andExpect(jsonPath("$.totalElementos").value(2));
        calendario(liga + "&equipoId=" + e.b.getId() + "&jornada=2").andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(p3));
        calendario(liga + "&estadioId=" + e.estadio1.getId()).andExpect(jsonPath("$.totalElementos").value(2));
        calendario(liga + "&estadioId=" + e.estadio2.getId()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(p2));
        // Paginación y orden pedido por el cliente.
        calendario(liga + "&size=2&page=1").andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        calendario(liga + "&sort=fechaHora,desc").andExpect(jsonPath("$.contenido[0].id").value(p3));
        // Un orden por una propiedad que no existe no provoca un 500: se ignora.
        calendario(liga + "&sort=propiedadInventada,asc").andExpect(jsonPath("$.totalElementos").value(3));
        // Una liga distinta no ve estos partidos.
        calendario("ligaId=" + new Escenario().liga.getId()).andExpect(jsonPath("$.totalElementos").value(0));
        assertThat(p1).isNotEqualTo(p2);
    }

    // =========================================================================== RN-04

    @Test
    @DisplayName("RN-04: un equipo contra sí mismo es 422 con código RN-04")
    void rn04_equipoContraSiMismo_devuelve422() throws Exception {
        Escenario e = new Escenario();

        programar(e.liga, 1, T1, e.a, e.a, e.estadio1, e.arbitro1)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("RN-04"));
    }

    // =========================================================================== RN-05

    @Test
    @DisplayName("RN-05: un equipo no juega dos partidos en la misma jornada de la liga, pero sí en otra jornada; "
            + "al editar no choca consigo mismo")
    void rn05_equipoEnLaMismaJornada() throws Exception {
        Escenario e = new Escenario();
        long p1 = idDe(programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());

        // A ya juega en la jornada 1 (como local): C-A con otro estadio, árbitro y hora tampoco puede ser en la 1.
        programar(e.liga, 1, T2, e.c, e.a, e.estadio2, e.arbitro2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-05"));
        // Lo mismo para el visitante B.
        programar(e.liga, 1, T2, e.b, e.d, e.estadio2, e.arbitro2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-05"));
        // En otra jornada sí puede.
        programar(e.liga, 2, T2, e.a, e.b, e.estadio2, e.arbitro2).andExpect(status().isCreated());

        // Reprogramar el partido 1 en su misma jornada no choca consigo mismo.
        mockMvc.perform(put(RUTA + "/" + p1).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(e.liga, 1, T2, e.a, e.b, e.estadio1, e.arbitro1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaHora").value("2026-03-08T23:00:00Z"));
    }

    // =========================================================================== RN-06

    @Test
    @DisplayName("RN-06: el estadio no puede tener dos partidos a la misma fecha y hora; a otra hora sí")
    void rn06_estadioALaMismaHora() throws Exception {
        Escenario e = new Escenario();
        programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated());

        programar(e.liga, 1, T1, e.c, e.d, e.estadio1, e.arbitro2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-06"));
        programar(e.liga, 1, T2, e.c, e.d, e.estadio1, e.arbitro2).andExpect(status().isCreated());
    }

    // =========================================================================== RN-07

    @Test
    @DisplayName("RN-07: el árbitro no puede dirigir dos partidos a la misma fecha y hora; a otra hora sí")
    void rn07_arbitroALaMismaHora() throws Exception {
        Escenario e = new Escenario();
        programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated());

        programar(e.liga, 1, T1, e.c, e.d, e.estadio2, e.arbitro1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-07"));
        programar(e.liga, 1, T2, e.c, e.d, e.estadio2, e.arbitro1).andExpect(status().isCreated());
    }

    // ================================================ Última defensa: la base de datos

    @Test
    @DisplayName("RN-06 y RN-07: los índices únicos de PostgreSQL rechazan el duplicado aunque se salte el servicio; "
            + "el CHECK rechaza un equipo contra sí mismo (RN-04)")
    void indicesUnicosYCheck_rechazanDuplicadosAunqueSeSalteElServicio() {
        Escenario e = new Escenario();
        Instant hora = Instant.parse("2026-04-04T19:00:00Z");
        partidoRepository.saveAndFlush(partido(e.liga, 1, hora, e.a, e.b, e.estadio1, e.arbitro1, EstadoPartido.PROGRAMADO));

        // Mismo estadio y misma hora, con otro árbitro y otros equipos: viola uq_partido_estadio_fecha.
        Partido mismoEstadio = partido(e.liga, 2, hora, e.c, e.d, e.estadio1, e.arbitro2, EstadoPartido.PROGRAMADO);
        assertThatThrownBy(() -> partidoRepository.saveAndFlush(mismoEstadio))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_partido_estadio_fecha");

        // Mismo árbitro y misma hora, con otro estadio y otros equipos: viola uq_partido_arbitro_fecha.
        Partido mismoArbitro = partido(e.liga, 2, hora, e.c, e.d, e.estadio2, e.arbitro1, EstadoPartido.PROGRAMADO);
        assertThatThrownBy(() -> partidoRepository.saveAndFlush(mismoArbitro))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_partido_arbitro_fecha");

        // Un equipo contra sí mismo: viola ck_partido_equipos_distintos.
        Partido consigoMismo = partido(e.liga, 3, hora.plusSeconds(3600), e.c, e.c, e.estadio2, e.arbitro2, EstadoPartido.PROGRAMADO);
        assertThatThrownBy(() -> partidoRepository.saveAndFlush(consigoMismo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_partido_equipos_distintos");

        // Con otra hora, el mismo estadio y el mismo árbitro sí pueden repetirse.
        assertThatCode(() -> partidoRepository.saveAndFlush(
                partido(e.liga, 2, hora.plusSeconds(7200), e.c, e.d, e.estadio1, e.arbitro1, EstadoPartido.PROGRAMADO)))
                .doesNotThrowAnyException();
    }

    // ============================================================ Ciclo de vida y RN-12

    @Test
    @DisplayName("Iniciar: PROGRAMADO pasa a EN_JUEGO (solo ADMIN); iniciar de nuevo es 409 PARTIDO_ESTADO_INVALIDO; "
            + "un partido EN_JUEGO no se edita ni se elimina")
    void iniciar_yPartidoEnJuego() throws Exception {
        Escenario e = new Escenario();
        long id = idDe(programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(post(RUTA + "/" + id + "/iniciar").header("Authorization", authUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA + "/" + id + "/iniciar").header("Authorization", authAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_JUEGO"));
        mockMvc.perform(post(RUTA + "/" + id + "/iniciar").header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_ESTADO_INVALIDO"));

        mockMvc.perform(put(RUTA + "/" + id).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(e.liga, 1, T2, e.a, e.b, e.estadio1, e.arbitro1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_EN_JUEGO"));
        mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_EN_JUEGO"));
    }

    @Test
    @DisplayName("RN-12: un partido FINALIZADO no se modifica ni se elimina (409 RN-12) y no se puede iniciar")
    void rn12_partidoFinalizado() throws Exception {
        Escenario e = new Escenario();
        Partido finalizado = partido(e.liga, 1, Instant.parse("2026-05-02T19:00:00Z"), e.a, e.b,
                e.estadio1, e.arbitro1, EstadoPartido.FINALIZADO);
        finalizado.setGolesLocal(2);
        finalizado.setGolesVisitante(1);
        long id = partidoRepository.saveAndFlush(finalizado).getId();

        mockMvc.perform(put(RUTA + "/" + id).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(e.liga, 1, T2, e.a, e.b, e.estadio1, e.arbitro1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-12"));
        mockMvc.perform(post(RUTA + "/" + id + "/iniciar").header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PARTIDO_ESTADO_INVALIDO"));

        // Nada cambió: sigue finalizado con su marcador.
        mockMvc.perform(get(RUTA + "/" + id).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.golesLocal").value(2))
                .andExpect(jsonPath("$.golesVisitante").value(1));
    }

    @Test
    @DisplayName("Un partido PROGRAMADO se elimina (204, solo ADMIN) y después es 404")
    void eliminarPartidoProgramado() throws Exception {
        Escenario e = new Escenario();
        long id = idDe(programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());

        mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", authUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(RUTA + "/" + id).header("Authorization", authAdmin()))
                .andExpect(status().isNotFound());
    }

    // ================================================ Borrado con dependientes (EST-02 y afines)

    @Test
    @DisplayName("Borrado con dependientes: estadio, árbitro, liga y equipos (local y visitante) con partidos dan 409; "
            + "sin partidos, no")
    void verificadoresDeBorrado_conYSinPartidos() throws Exception {
        Escenario e = new Escenario();
        long id = idDe(programar(e.liga, 1, T1, e.a, e.b, e.estadio1, e.arbitro1).andExpect(status().isCreated()).andReturn());

        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Estadio.class, e.estadio1.getId()))
                .isInstanceOfSatisfying(ConflictoException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("EST-02"));
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Arbitro.class, e.arbitro1.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Liga.class, e.liga.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("CONFLICTO_DEPENDENCIAS"));
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Equipo.class, e.a.getId()))
                .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> verificadoresEliminacion.verificar(Equipo.class, e.b.getId()))
                .isInstanceOf(ConflictoException.class);

        // Sin partidos no hay conflicto: el segundo estadio, el segundo árbitro, los equipos C y D y una liga
        // nueva (sin equipos ni partidos, para no depender de los verificadores de la Fase 3) están libres.
        Long ligaSinNada = nuevaLiga().getId();
        assertThatCode(() -> {
            verificadoresEliminacion.verificar(Estadio.class, e.estadio2.getId());
            verificadoresEliminacion.verificar(Arbitro.class, e.arbitro2.getId());
            verificadoresEliminacion.verificar(Equipo.class, e.c.getId());
            verificadoresEliminacion.verificar(Equipo.class, e.d.getId());
            verificadoresEliminacion.verificar(Liga.class, ligaSinNada);
        }).doesNotThrowAnyException();

        // Al eliminar el partido, el estadio, el árbitro y los equipos vuelven a poder eliminarse.
        mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        assertThatCode(() -> {
            verificadoresEliminacion.verificar(Estadio.class, e.estadio1.getId());
            verificadoresEliminacion.verificar(Arbitro.class, e.arbitro1.getId());
            verificadoresEliminacion.verificar(Equipo.class, e.a.getId());
            verificadoresEliminacion.verificar(Equipo.class, e.b.getId());
        }).doesNotThrowAnyException();
    }

    // =========================================================================== Apoyo

    private ResultActions programar(Liga liga, int jornada, String fechaHora, Equipo local, Equipo visitante,
                                    Estadio estadio, Arbitro arbitro) throws Exception {
        return mockMvc.perform(post(RUTA)
                .header("Authorization", authAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(liga, jornada, fechaHora, local, visitante, estadio, arbitro)));
    }

    private ResultActions calendario(String consulta) throws Exception {
        return mockMvc.perform(get(RUTA + "?" + consulta).header("Authorization", authUser()))
                .andExpect(status().isOk());
    }

    private static String cuerpo(Liga liga, int jornada, String fechaHora, Equipo local, Equipo visitante,
                                 Estadio estadio, Arbitro arbitro) {
        return """
                {"ligaId": %d, "jornada": %d, "fechaHora": "%s", "equipoLocalId": %d, "equipoVisitanteId": %d,
                 "estadioId": %d, "arbitroId": %d}
                """.formatted(liga.getId(), jornada, fechaHora, local.getId(), visitante.getId(),
                estadio.getId(), arbitro.getId());
    }

    private static long idDe(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.<Number>read(cuerpo, "$.id").longValue();
    }

    private static Partido partido(Liga liga, int jornada, Instant fechaHora, Equipo local, Equipo visitante,
                                   Estadio estadio, Arbitro arbitro, EstadoPartido estado) {
        Partido partido = new Partido();
        partido.setLiga(liga);
        partido.setJornada(jornada);
        partido.setFechaHora(fechaHora);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setEstadio(estadio);
        partido.setArbitro(arbitro);
        partido.setEstado(estado);
        return partido;
    }

    /** Una liga con cuatro equipos, dos estadios y dos árbitros, todos con nombres únicos. */
    private final class Escenario {
        final Liga liga = nuevaLiga();
        final Equipo a = nuevoEquipo(liga, "A");
        final Equipo b = nuevoEquipo(liga, "B");
        final Equipo c = nuevoEquipo(liga, "C");
        final Equipo d = nuevoEquipo(liga, "D");
        final Estadio estadio1 = nuevoEstadio();
        final Estadio estadio2 = nuevoEstadio();
        final Arbitro arbitro1 = nuevoArbitro();
        final Arbitro arbitro2 = nuevoArbitro();
    }

    private Liga nuevaLiga() {
        Liga liga = new Liga();
        liga.setNombre(unico("Liga IT"));
        liga.setTemporada(unico("2026"));
        liga.setFechaInicio(LocalDate.of(2026, 1, 1));
        liga.setFechaFin(LocalDate.of(2026, 12, 31));
        return ligaRepository.save(liga);
    }

    private Estadio nuevoEstadio() {
        Estadio estadio = new Estadio();
        estadio.setNombre(unico("Estadio IT"));
        estadio.setCiudad("Cali");
        estadio.setCapacidad(30_000);
        return estadioRepository.save(estadio);
    }

    private Arbitro nuevoArbitro() {
        Arbitro arbitro = new Arbitro();
        arbitro.setNombre(unico("Arbitro IT"));
        arbitro.setNacionalidad("Colombia");
        arbitro.setAniosExperiencia(5);
        return arbitroRepository.save(arbitro);
    }

    private Equipo nuevoEquipo(Liga liga, String letra) {
        Entrenador entrenador = new Entrenador();
        entrenador.setNombre(unico("Entrenador IT " + letra));
        entrenador.setNacionalidad("Colombia");
        entrenador = entrenadorRepository.save(entrenador);

        Equipo equipo = new Equipo();
        equipo.setNombre(unico("Equipo IT " + letra));
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1990);
        equipo.setEstadioLocal(nuevoEstadio());
        equipo.setEntrenador(entrenador);
        equipo.setLiga(liga);
        return equipoRepository.save(equipo);
    }
}

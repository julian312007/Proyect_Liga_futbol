package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.entity.Arbitro;
import co.edu.uniajc.liga.entity.Entrenador;
import co.edu.uniajc.liga.entity.Equipo;
import co.edu.uniajc.liga.entity.Estadio;
import co.edu.uniajc.liga.entity.Liga;
import co.edu.uniajc.liga.repository.ArbitroRepository;
import co.edu.uniajc.liga.repository.EntrenadorRepository;
import co.edu.uniajc.liga.repository.EquipoRepository;
import co.edu.uniajc.liga.repository.EstadioRepository;
import co.edu.uniajc.liga.repository.LigaRepository;
import co.edu.uniajc.liga.support.BaseIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de la Fase 2 (catálogos) con la aplicación completa, PostgreSQL real (Testcontainers, Flyway con
 * las migraciones V3 a V6 y {@code ddl-auto=validate}) y la seguridad real, con un ADMIN y un USER reales
 * guardados en la base de datos ({@code authAdmin()} / {@code authUser()}). Requiere Docker; corre en
 * {@code mvn verify}. Hereda de {@link BaseIT} para reutilizar el contexto de Spring.
 *
 * <p>Cubre: creación real por HTTP, los índices únicos y los CHECK de la base de datos (última defensa de
 * EST-01, EST-03 y de las ligas), y el borrado con dependientes (EST-02). Para este último crea un
 * {@code Equipo} con el repositorio base: con la Fase 3 integrada responde el verificador
 * ({@code EST-02} / {@code CONFLICTO_DEPENDENCIAS}); sin ella, la clave foránea de la base de datos
 * ({@code CONFLICTO_INTEGRIDAD}). En ambos casos es un 409 y el registro sigue existiendo, que es lo que se
 * comprueba. Todos los datos llevan nombres únicos ({@code unico(...)}): no se asume que las tablas estén vacías.
 */
class CatalogosIT extends BaseIT {

    private static final String ESTADIOS = "/api/v1/estadios";
    private static final String ARBITROS = "/api/v1/arbitros";
    private static final String ENTRENADORES = "/api/v1/entrenadores";
    private static final String LIGAS = "/api/v1/ligas";

    @Autowired
    private EstadioRepository estadioRepository;

    @Autowired
    private ArbitroRepository arbitroRepository;

    @Autowired
    private EntrenadorRepository entrenadorRepository;

    @Autowired
    private LigaRepository ligaRepository;

    @Autowired
    private EquipoRepository equipoRepository;

    // ===================================================================== apoyo

    private record Creado(long id, String ubicacion) {
    }

    private static String jsonEstadio(String nombre, String ciudad, int capacidad) {
        return "{\"nombre\": \"%s\", \"ciudad\": \"%s\", \"capacidad\": %d}".formatted(nombre, ciudad, capacidad);
    }

    private static String jsonLiga(String nombre, String temporada, String inicio, String fin) {
        return "{\"nombre\": \"%s\", \"temporada\": \"%s\", \"fechaInicio\": \"%s\", \"fechaFin\": \"%s\"}"
                .formatted(nombre, temporada, inicio, fin);
    }

    private static String jsonArbitro(String nombre, int anios) {
        return "{\"nombre\": \"%s\", \"nacionalidad\": \"Colombia\", \"aniosExperiencia\": %d}".formatted(nombre, anios);
    }

    private static String jsonEntrenador(String nombre) {
        return "{\"nombre\": \"%s\", \"nacionalidad\": \"Brasil\"}".formatted(nombre);
    }

    /** POST como ADMIN real; exige 201 y devuelve el id y la cabecera Location. */
    private Creado crear(String ruta, String cuerpo) throws Exception {
        MvcResult resultado = mockMvc.perform(post(ruta)
                        .header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        long id = ((Number) JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"))
                .longValue();
        String ubicacion = resultado.getResponse().getHeader("Location");
        assertThat(ubicacion).endsWith(ruta + "/" + id);
        return new Creado(id, ubicacion);
    }

    private Estadio estadioEnBd(String nombre, String ciudad, int capacidad) {
        Estadio estadio = new Estadio();
        estadio.setNombre(nombre);
        estadio.setCiudad(ciudad);
        estadio.setCapacidad(capacidad);
        return estadio;
    }

    private Liga ligaEnBd(String nombre, String temporada, LocalDate inicio, LocalDate fin) {
        Liga liga = new Liga();
        liga.setNombre(nombre);
        liga.setTemporada(temporada);
        liga.setFechaInicio(inicio);
        liga.setFechaFin(fin);
        return liga;
    }

    // ============================================================ Estadio: crear y leer

    @Test
    @DisplayName("Estadio: POST con ADMIN real da 201 con Location; el GET de esa URL (USER real) lo devuelve normalizado y la BD lo guarda")
    void estadio_crearYLeer() throws Exception {
        String nombre = unico("Estadio Pascual");

        Creado creado = crear(ESTADIOS, jsonEstadio("  " + nombre.replace(" ", "   ") + "  ", " Cali ", 38_000));

        mockMvc.perform(get(creado.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.id()))
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andExpect(jsonPath("$.ciudad").value("Cali"))
                .andExpect(jsonPath("$.capacidad").value(38_000));
        Estadio guardado = estadioRepository.findById(creado.id()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo(nombre);
        assertThat(guardado.getCiudad()).isEqualTo("Cali");
    }

    @Test
    @DisplayName("Roles reales: sin token 401, USER escribiendo 403, USER leyendo 200")
    void estadio_matrizDeRoles() throws Exception {
        String cuerpo = jsonEstadio(unico("Estadio roles"), "Cali", 1_000);

        mockMvc.perform(post(ESTADIOS).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(post(ESTADIOS).header("Authorization", authUser())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mockMvc.perform(get(ESTADIOS).header("Authorization", authUser()))
                .andExpect(status().isOk());
    }

    // ===================================================================== EST-01

    @Test
    @DisplayName("EST-01: el mismo nombre en la misma ciudad, aunque cambien las mayúsculas y los espacios, da 409; en otra ciudad es válido")
    void est01_duplicadoPorCiudad() throws Exception {
        String nombre = unico("Estadio Duplicado");
        crear(ESTADIOS, jsonEstadio(nombre, "Cali", 1_000));

        mockMvc.perform(post(ESTADIOS).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstadio("  " + nombre.toUpperCase().replace(" ", "  ") + " ", "cali", 2_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EST-01"));

        crear(ESTADIOS, jsonEstadio(nombre, "Palmira", 3_000));
    }

    @Test
    @DisplayName("EST-01: PUT con su propio nombre es válido (200); PUT con el nombre de otro estadio de la ciudad da 409")
    void est01_actualizarExcluyeAlPropioRegistro() throws Exception {
        String nombreA = unico("Estadio A");
        String nombreB = unico("Estadio B");
        Creado a = crear(ESTADIOS, jsonEstadio(nombreA, "Cali", 1_000));
        crear(ESTADIOS, jsonEstadio(nombreB, "Cali", 1_000));

        mockMvc.perform(put(ESTADIOS + "/" + a.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEstadio(nombreA, "Cali", 5_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidad").value(5_000));

        mockMvc.perform(put(ESTADIOS + "/" + a.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEstadio(nombreB.toLowerCase(), "Cali", 5_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EST-01"));
        assertThat(estadioRepository.findById(a.id()).orElseThrow().getNombre()).isEqualTo(nombreA);
    }

    @Test
    @DisplayName("EST-01 (última defensa): el índice único de la BD rechaza el duplicado por ciudad sin distinguir mayúsculas")
    void est01_indiceUnicoDeLaBaseDeDatos() {
        String nombre = unico("Estadio Indice");
        estadioRepository.saveAndFlush(estadioEnBd(nombre, "Cali", 1_000));

        assertThatThrownBy(() -> estadioRepository.saveAndFlush(estadioEnBd(nombre.toUpperCase(), "CALI", 2_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // En otra ciudad sí puede repetirse.
        estadioRepository.saveAndFlush(estadioEnBd(nombre, "Palmira", 2_000));
    }

    // ===================================================================== EST-03

    @Test
    @DisplayName("EST-03: capacidad cero o ciudad vacía dan 400 por HTTP; la BD también rechaza capacidad <= 0 (CHECK)")
    void est03_capacidadYCiudad() throws Exception {
        mockMvc.perform(post(ESTADIOS).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEstadio(unico("Estadio"), "Cali", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(post(ESTADIOS).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEstadio(unico("Estadio"), "", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        assertThatThrownBy(() -> estadioRepository.saveAndFlush(estadioEnBd(unico("Estadio check"), "Cali", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ================================================================= paginación

    @Test
    @DisplayName("Listado paginado: respeta page/size/sort; un sort desconocido no da 500; size enorme se recorta a 100")
    void estadio_listadoPaginado() throws Exception {
        String prefijo = unico("Estadio Pag");
        for (int i = 1; i <= 3; i++) {
            crear(ESTADIOS, jsonEstadio(prefijo + " " + i, "Cali", 1_000 * i));
        }

        mockMvc.perform(get(ESTADIOS).param("size", "2").param("sort", "capacidad,desc")
                        .header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.tamanio").value(2))
                .andExpect(jsonPath("$.totalElementos").value(greaterThanOrEqualTo(3)));
        mockMvc.perform(get(ESTADIOS).param("sort", "propiedadQueNoExiste").header("Authorization", authUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get(ESTADIOS).param("size", "100000").header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanio").value(100));
    }

    // ================================================== EST-02: borrado con dependientes

    @Test
    @DisplayName("EST-02: un estadio que es local de un equipo no se elimina (409) y sigue existiendo; sin el equipo, DELETE da 204 y luego 404")
    void est02_borradoConDependientes() throws Exception {
        Creado estadio = crear(ESTADIOS, jsonEstadio(unico("Estadio Local"), "Cali", 1_000));
        Creado entrenador = crear(ENTRENADORES, jsonEntrenador(unico("Entrenador Local")));
        Creado liga = crear(LIGAS, jsonLiga(unico("Liga Local"), "2026-I", "2026-02-01", "2026-06-30"));
        Equipo equipo = equipoRepository.save(equipoEnBd(estadio.id(), entrenador.id(), liga.id()));

        mockMvc.perform(delete(ESTADIOS + "/" + estadio.id()).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", anyOf(equalTo("EST-02"), equalTo("CONFLICTO_INTEGRIDAD"))));
        mockMvc.perform(get(estadio.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isOk());

        equipoRepository.deleteById(equipo.getId());

        mockMvc.perform(delete(ESTADIOS + "/" + estadio.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(estadio.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Un entrenador que dirige a un equipo y una liga con equipos no se eliminan (409); sin dependientes, sí (204)")
    void entrenadorYLiga_borradoConDependientes() throws Exception {
        Creado estadio = crear(ESTADIOS, jsonEstadio(unico("Estadio Dep"), "Cali", 1_000));
        Creado entrenador = crear(ENTRENADORES, jsonEntrenador(unico("Entrenador Dep")));
        Creado liga = crear(LIGAS, jsonLiga(unico("Liga Dep"), "2026-I", "2026-02-01", "2026-06-30"));
        Equipo equipo = equipoRepository.save(equipoEnBd(estadio.id(), entrenador.id(), liga.id()));

        mockMvc.perform(delete(ENTRENADORES + "/" + entrenador.id()).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", anyOf(equalTo("CONFLICTO_DEPENDENCIAS"), equalTo("CONFLICTO_INTEGRIDAD"))));
        mockMvc.perform(delete(LIGAS + "/" + liga.id()).header("Authorization", authAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", anyOf(equalTo("CONFLICTO_DEPENDENCIAS"), equalTo("CONFLICTO_INTEGRIDAD"))));
        mockMvc.perform(get(entrenador.ubicacion()).header("Authorization", authUser())).andExpect(status().isOk());
        mockMvc.perform(get(liga.ubicacion()).header("Authorization", authUser())).andExpect(status().isOk());

        equipoRepository.deleteById(equipo.getId());

        mockMvc.perform(delete(ENTRENADORES + "/" + entrenador.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(LIGAS + "/" + liga.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(liga.ubicacion()).header("Authorization", authUser())).andExpect(status().isNotFound());
    }

    private Equipo equipoEnBd(long estadioId, long entrenadorId, long ligaId) {
        Equipo equipo = new Equipo();
        equipo.setNombre(unico("Equipo"));
        equipo.setCiudad("Cali");
        equipo.setAnioFundacion(1990);
        equipo.setEstadioLocal(estadioRepository.findById(estadioId).orElseThrow());
        equipo.setEntrenador(entrenadorRepository.findById(entrenadorId).orElseThrow());
        equipo.setLiga(ligaRepository.findById(ligaId).orElseThrow());
        return equipo;
    }

    // ======================================================================= Liga

    @Test
    @DisplayName("Liga: crear (201), repetir nombre y temporada (409 LIGA_DUPLICADA), fechas incoherentes (422), otra temporada (201)")
    void liga_reglas() throws Exception {
        String nombre = unico("Liga");
        Creado creada = crear(LIGAS, jsonLiga(nombre, "2026-I", "2026-02-01", "2026-06-30"));

        mockMvc.perform(get(creada.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicio").value("2026-02-01"))
                .andExpect(jsonPath("$.fechaFin").value("2026-06-30"));
        mockMvc.perform(post(LIGAS).header("Authorization", authAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLiga(nombre, "2026-I", "2026-03-01", "2026-07-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIGA_DUPLICADA"));
        mockMvc.perform(post(LIGAS).header("Authorization", authAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLiga(unico("Liga"), "2026-I", "2026-06-30", "2026-02-01")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("LIGA_FECHAS_INVALIDAS"));
        crear(LIGAS, jsonLiga(nombre, "2026-II", "2026-08-01", "2026-12-15"));
    }

    @Test
    @DisplayName("Liga: PUT con su propio nombre y temporada es válido (200); PUT a los de otra liga da 409; DELETE sin dependientes da 204")
    void liga_actualizarYEliminar() throws Exception {
        String nombreA = unico("Liga A");
        String nombreB = unico("Liga B");
        Creado a = crear(LIGAS, jsonLiga(nombreA, "2026-I", "2026-02-01", "2026-06-30"));
        crear(LIGAS, jsonLiga(nombreB, "2026-I", "2026-02-01", "2026-06-30"));

        mockMvc.perform(put(LIGAS + "/" + a.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLiga(nombreA, "2026-I", "2026-02-01", "2026-07-15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaFin").value("2026-07-15"));
        mockMvc.perform(put(LIGAS + "/" + a.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLiga(nombreB, "2026-I", "2026-02-01", "2026-07-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIGA_DUPLICADA"));

        mockMvc.perform(delete(LIGAS + "/" + a.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(a.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Liga (última defensa): la BD rechaza (nombre, temporada) repetidos y fechaFin <= fechaInicio (restricción UNIQUE y CHECK)")
    void liga_restriccionesDeLaBaseDeDatos() {
        String nombre = unico("Liga BD");
        ligaRepository.saveAndFlush(ligaEnBd(nombre, "2026-I", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 30)));

        assertThatThrownBy(() -> ligaRepository.saveAndFlush(
                ligaEnBd(nombre, "2026-I", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 30))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ligaRepository.saveAndFlush(
                ligaEnBd(unico("Liga BD"), "2026-I", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ============================================================= Arbitro y Entrenador

    @Test
    @DisplayName("Árbitro: crear con 0 años (201), años negativos (400), PUT, DELETE (204) y luego 404; la BD rechaza años negativos (CHECK)")
    void arbitro_crudYRegla() throws Exception {
        Creado creado = crear(ARBITROS, jsonArbitro(unico("Árbitro"), 0));

        mockMvc.perform(post(ARBITROS).header("Authorization", authAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonArbitro(unico("Árbitro"), -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(put(ARBITROS + "/" + creado.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonArbitro(unico("Árbitro editado"), 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aniosExperiencia").value(7));
        mockMvc.perform(get(ARBITROS).header("Authorization", authUser()))
                .andExpect(status().isOk());

        Arbitro negativo = new Arbitro();
        negativo.setNombre(unico("Árbitro BD"));
        negativo.setAniosExperiencia(-1);
        assertThatThrownBy(() -> arbitroRepository.saveAndFlush(negativo))
                .isInstanceOf(DataIntegrityViolationException.class);

        mockMvc.perform(delete(ARBITROS + "/" + creado.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(creado.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Entrenador: crear (201), nombre vacío (400), USER escribiendo (403), PUT, DELETE (204) y luego 404")
    void entrenador_crudYRegla() throws Exception {
        Creado creado = crear(ENTRENADORES, jsonEntrenador(unico("Entrenador")));

        mockMvc.perform(post(ENTRENADORES).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEntrenador("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(post(ENTRENADORES).header("Authorization", authUser())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEntrenador(unico("Entrenador"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ENTRENADORES + "/" + creado.id()).header("Authorization", authAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonEntrenador(unico("Entrenador editado"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(ENTRENADORES + "/" + creado.id()).header("Authorization", authAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(creado.ubicacion()).header("Authorization", authUser()))
                .andExpect(status().isNotFound());
        assertThat(entrenadorRepository.findById(creado.id())).isEmpty();
    }
}

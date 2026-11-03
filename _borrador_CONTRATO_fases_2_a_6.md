# CONTRATO COMÚN — Fases 2 a 6 (borradores en paralelo)

Documento de trabajo del 2026-11-02. Lo obedecen los cinco agentes (uno por fase) para que sus borradores
encajen al integrarlos en `src/`. **Prevalece sobre cualquier suposición propia.** Si algo del contrato choca
con `PROMPT_Liga_Futbol.md`, gana la sección 0 del PROMPT y, después, este contrato; si no puedes resolverlo,
anótalo en tu informe (no lo rellenes en silencio).

Contexto de fondo: `CLAUDE.md`, `ESTATUS.md` y `PROMPT_Liga_Futbol.md` (secciones 3, 4.5, 4.6, 5, 6, 7, 8, 11 y 12).
La autenticación ya existe (Fase 1, con Google): **no la toques**.

---

## 1. Reglas de trabajo (obligatorias)

1. **Dónde escribes.** Solo en tu carpeta de borrador `D:\Proyect_Liga_futbol\_borrador_faseN\`, replicando la ruta
   relativa que tendría cada archivo desde la raíz del proyecto (por ejemplo
   `_borrador_fase3\src\main\java\co\edu\uniajc\liga\service\impl\EquipoServiceImpl.java`).
2. **Qué no tocas jamás:** `src\` del proyecto, `pom.xml`, `target\`, `.mvn\`, `.env`, `docker-compose.yml`,
   `ESTATUS.md`, `CLAUDE.md`, `PROMPT_Liga_Futbol.md`, `_borrador_fase1\`, `_borrador_dominio\` y las carpetas de
   otras fases. Si necesitas un cambio en algo que no es tuyo, descríbelo en tu informe.
3. **No ejecutes** Maven, `mvnw`, Docker ni `docker compose`. No hagas commits ni borres archivos que no hayas
   creado tú. No hay secretos en el código.
4. **Idioma:** dominio en español; sufijos técnicos en inglés (`Service`, `Repository`, `Controller`, `Mapper`);
   Javadoc, comentarios, mensajes de error y `@DisplayName` en español.
5. **Calidad:** inyección por constructor; sin `@Data` en entidades; controladores delgados (solo `@Valid`,
   `@PreAuthorize`, delegar y devolver DTO); toda regla de negocio vive en el servicio; los DTO son `record`;
   ningún controlador usa un repositorio; nunca se expone una entidad.
6. **No inventes reglas.** Implementa las del plan. Si crees que falta una (por ejemplo, que los equipos de un
   partido pertenezcan a su liga), no la añadas: anótala en el informe como propuesta con su pro y su contra.
7. **Verifica antes de entregar** con la herramienta de la sección 9. Un borrador que no compila o cuyas
   pruebas fallan **no está terminado**: arréglalo. No declares nada "hecho" sin esa evidencia.
8. **Honestidad:** en el informe separa lo que compiló y pasó pruebas de lo que no se pudo comprobar (por
   ejemplo, todo lo que requiere Docker/PostgreSQL: los `*IT`, las migraciones y `ddl-auto=validate`).

## 2. Propiedad de archivos (para que nadie pise a nadie)

Cada fase crea **clases nuevas** y solo reemplaza los archivos que le pertenecen. Ninguna fase modifica
`application.yaml` (nadie lo necesita) ni `SecurityConfig`, `GlobalExceptionHandler` ni `OpenApiConfig`.

| Fase | Entidades / repositorios que reemplaza (están en `_borrador_dominio`) | Archivo de configuración que puede editar |
|---|---|---|
| 2 | `LigaRepository`, `EstadioRepository`, `EntrenadorRepository`, `ArbitroRepository` | ninguno |
| 3 | `EquipoRepository`, `JugadorRepository` | ninguno |
| 4 | `PartidoRepository` | ninguno |
| 5 | `GolRepository`, `TarjetaRepository` | ninguno |
| 6 | ninguno (crea repositorios nuevos) | `src/test/resources/application-test.yaml` (solo para `spring.cache.type=simple`, partiendo del archivo actual) |

**Regla de consultas:** si tu servicio necesita una consulta sobre una entidad de otra fase, **no edites el
repositorio de esa fase**; crea un repositorio nuevo en tu fase (por ejemplo `PartidoDeEquipoRepository
extends JpaRepository<Partido, Long>`), nombrado por su propósito. De otras fases usa solo: las entidades y
enums (getters/setters), `findById`/`existsById`/`getReferenceById`/`save` del repositorio base y los tipos
compartidos de la sección 4.

`_borrador_dominio\` ya contiene (y **es de solo lectura para ti**): enums, las 9 entidades, un repositorio base
por entidad, las migraciones `V3` a `V11`, `ResumenResponse`, `PaginaResponse`, `SuspendidoResponse`,
`NombresCache`, `VerificadorEliminacion`, `VerificadoresEliminacion`, `PaginacionSegura`, `SuspensionService`
y `support/BaseIT`. Léelos antes de empezar: las entidades son el contrato de campos y tipos.

## 3. Modelo (resumen; la fuente de verdad son las entidades de `_borrador_dominio`)

| Entidad (paquete `entity`) | Campos Java | Tabla / id | Fase |
|---|---|---|---|
| `Liga` | id, nombre, temporada, fechaInicio, fechaFin | `liga` / `id_liga` | 2 |
| `Estadio` | id, nombre, ciudad, capacidad | `estadio` / `id_estadio` | 2 |
| `Entrenador` | id, nombre, nacionalidad | `entrenador` / `id_entrenador` | 2 |
| `Arbitro` | id, nombre, nacionalidad, aniosExperiencia | `arbitro` / `id_arbitro` | 2 |
| `Equipo` | id, nombre, ciudad, anioFundacion, estadioLocal, entrenador, liga | `equipo` / `id_equipo` | 3 |
| `Jugador` | id, nombre, fechaNacimiento, posicion, dorsal, equipo | `jugador` / `id_jugador` | 3 |
| `Partido` | id, liga, jornada, fechaHora (`Instant`), equipoLocal, equipoVisitante, estadio, arbitro, golesLocal, golesVisitante, estado | `partido` / `id_partido` | 4 |
| `Gol` | id, partido, jugador, minuto, tipo | `gol` / `id_gol` | 5 |
| `Tarjeta` | id, partido, jugador, tipo, minuto | `tarjeta` / `id_tarjeta` | 5 |

Enums: `PosicionJugador`, `TipoGol`, `TipoTarjeta`, `EstadoPartido`. Relaciones `LAZY`. **Las longitudes de
`@Column` ya coinciden con las migraciones**; no las cambies (si crees que hay que cambiar algo del dominio,
repórtalo). Migraciones: `V3 estadio, V4 arbitro, V5 entrenador, V6 liga` (Fase 2) · `V7 equipo, V8 jugador`
(Fase 3) · `V9 partido` (Fase 4) · `V10 gol, V11 tarjeta` (Fase 5). **Solo la Fase 6 puede crear `V12+`**, y solo
si una consulta realmente necesita un índice nuevo.

## 4. Tipos compartidos (ya escritos en `_borrador_dominio`)

- `dto.response.ResumenResponse(Long id, String nombre)`: resumen de una relación en los `XResponse`.
  **Cada mapper declara sus propios métodos `default ResumenResponse resumen(X x)`** (con control de `null`) para
  las relaciones que expone; no uses `uses = {...}` entre mappers.
- `dto.response.PaginaResponse<T>` con `PaginaResponse.de(Page<T>)`: todo listado paginado devuelve
  `PaginaResponse<XResponse>`. El controlador recibe `Pageable` y el servicio lo pasa por
  `PaginacionSegura.normalizar(pageable, permitidas, ordenPorDefecto)` antes de consultar.
- `service.VerificadorEliminacion` + `service.VerificadoresEliminacion` (punto de extensión de borrado, ver §6).
- `config.NombresCache.TABLA_POSICIONES` y `.GOLEADORES` (cachés de la Fase 6; clave = id de la liga, `Long`).
- `service.SuspensionService` (interfaz) y `dto.response.SuspendidoResponse`: la **Fase 5 implementa**
  `SuspensionService`; la **Fase 6 la consume**.

## 5. Convenciones de código (modelos a copiar: archivos de la Fase 1 ya compilados en `src/`)

- Entidad: `entity/Usuario.java`. Migración: `db/migration/V2__usuario.sql`. Mapper: `mapper/UsuarioMapper.java`
  (`@Mapper` sin más: el modelo de componente `spring` viene por argumento del compilador). Servicio:
  `service/AuthService.java` + `service/impl/AuthServiceImpl.java`. Controlador: `controller/AuthController.java`
  (`@Tag`, `@Operation`, `@ApiResponses`). Errores: `exception/` (no los modifiques).
- **Excepciones:** `RecursoNoEncontradoException("Estadio", id)` → 404; `ConflictoException(codigo, mensaje)` → 409;
  `ReglaNegocioException(codigo, mensaje)` → 422. El `codigo` es el de la regla (`"RN-05"`, `"EST-01"`...) o, si no
  hay regla del plan, un `SCREAMING_SNAKE_CASE` descriptivo (`"LIGA_DUPLICADA"`, `"DORSAL_DUPLICADO"`...). La
  validación del cuerpo (Bean Validation) da 400 sola. `DataIntegrityViolationException` ya se traduce a 409.
  Comprueba en `GlobalExceptionHandler` qué más cubre (JSON mal formado, enum inválido, tipo de parámetro); si
  falta algo, repórtalo, no lo edites.
- **Servicio:** interfaz en `service/` + implementación en `service/impl/`; `@Service`; `@Transactional` en las
  operaciones que escriben y `@Transactional(readOnly = true)` en las de lectura; devuelve DTO (el mapeo a
  `XResponse` ocurre **dentro** de la transacción: `open-in-view` está en `false`, una relación `LAZY` fuera de
  la transacción lanza `LazyInitializationException`). Resuelve las relaciones por id en el servicio y usa
  `findById(...).orElseThrow(() -> new RecursoNoEncontradoException(...))`.
- **Normalización:** `trim()` y colapso de espacios en los textos de entrada antes de comparar o guardar.
- **DTO de entrada:** `record XRequest` con Bean Validation y mensajes en español; las relaciones llegan por id
  (`estadioLocalId`, `ligaId`...). **Nunca** un campo `id`, `rol` ni estado que el cliente no deba fijar.
- **Mapper:** `toEntity(XRequest)` ignorando `id` y las relaciones (`@Mapping(target = "...", ignore = true)`),
  `toResponse(X)` y `updateEntity(XRequest, @MappingTarget X)` ignorando `id` y relaciones. Sin repositorios.
- **Controlador:** `@RestController @RequestMapping("/api/v1/<recurso>")`, `@Tag`, `@Operation`, `@ApiResponses`.
  `GET` → `@PreAuthorize("hasAnyRole('USER','ADMIN')")`; `POST/PUT/DELETE` y acciones →
  `@PreAuthorize("hasRole('ADMIN')")`. `POST` crea y devuelve **201 + cabecera `Location`**
  (`ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()`); `PUT` devuelve
  200 con el recurso; `DELETE` devuelve 204. Los grupos de Swagger ya existen en `application.yaml`
  (`/api/v1/ligas|estadios|arbitros|entrenadores/**` = catálogos; `equipos|jugadores` = equipos y jugadores;
  `partidos` = partidos, goles y tarjetas; `/ligas/*/tabla-posiciones|goleadores|sanciones/**` = estadísticas).
- Fechas y horas: `fechaHora` es `Instant` (ISO-8601 con zona, por ejemplo `2026-02-01T20:00:00-05:00`).

## 6. Punto de extensión de borrado (409 si hay dependientes)

El servicio de cada entidad que otra fase referencia llama, justo antes de borrar:
`verificadoresEliminacion.verificar(Estadio.class, id)` (clase `VerificadoresEliminacion`, inyectada por
constructor). Cada fase posterior publica un `@Component` que implementa `VerificadorEliminacion` y lanza
`ConflictoException` si hay dependientes, **sin tocar los servicios de la fase anterior**:

| Verificador (bean) | Entidad vigilada | Lo crea | Condición para 409 |
|---|---|---|---|
| `EstadioConEquiposVerificador` | `Estadio` | Fase 3 | algún equipo lo tiene de estadio local — código `EST-02` |
| `EntrenadorConEquipoVerificador` | `Entrenador` | Fase 3 | está asignado a un equipo — `CONFLICTO_DEPENDENCIAS` |
| `LigaConEquiposVerificador` | `Liga` | Fase 3 | tiene equipos — `CONFLICTO_DEPENDENCIAS` |
| `EstadioConPartidosVerificador` | `Estadio` | Fase 4 | tiene partidos — `EST-02` |
| `ArbitroConPartidosVerificador` | `Arbitro` | Fase 4 | tiene partidos — `CONFLICTO_DEPENDENCIAS` |
| `LigaConPartidosVerificador` | `Liga` | Fase 4 | tiene partidos — `CONFLICTO_DEPENDENCIAS` |
| `EquipoConPartidosVerificador` | `Equipo` | Fase 4 | juega o jugó partidos — `CONFLICTO_DEPENDENCIAS` |
| `JugadorConEventosVerificador` | `Jugador` | Fase 5 | tiene goles o tarjetas — `CONFLICTO_DEPENDENCIAS` |

Los servicios que llaman a `verificadoresEliminacion.verificar(...)`: `EstadioService`, `EntrenadorService`,
`ArbitroService`, `LigaService` (Fase 2), `EquipoService` (Fase 3; además comprueba sus jugadores con su propio
repositorio) y `JugadorService` (Fase 3). Cada verificador se prueba con su propio test unitario (positivo y
negativo). **EST-02 queda completa cuando existen las fases 3 y 4**; en la Fase 2 sola solo se prueba que el
servicio consulta a los verificadores y propaga el 409.

## 7. Endpoints y reglas por fase

Todas las rutas con prefijo `/api/v1`. `GET` = `USER` o `ADMIN`; el resto = `ADMIN`. Listados paginados
(`?page=&size=&sort=`).

**Fase 2 — Catálogos** (F1 parcial, D-4): `/estadios`, `/arbitros`, `/entrenadores`, `/ligas` con
`GET` (paginado) · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}`. Reglas: **EST-01** (nombre normalizado y
único por ciudad sin distinguir mayúsculas → 409; en crear y actualizar; al actualizar se excluye el propio
registro), **EST-02** (§6), **EST-03** (`capacidad` > 0 y `ciudad` obligatoria → 400). Liga: nombre y temporada
obligatorios, `fechaFin` > `fechaInicio` (→ 422, código `LIGA_FECHAS_INVALIDAS`), único (nombre, temporada) → 409.
Arbitro: `aniosExperiencia` ≥ 0. Entrenador: nombre obligatorio. `LigaServiceImpl.eliminar` lleva
`@CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#id")` (no hace nada
hasta la Fase 6). Estadio es el controlador de referencia de D-4: pruebas de cada regla y de la matriz de roles.

**Fase 3 — Equipo y Jugador** (F1, F6 parcial; RN-02, RN-03): `/equipos` y `/jugadores` con el CRUD de arriba,
más `GET /equipos/{id}/plantel` (lista de jugadores del equipo, paginada) y `GET /equipos/{id}/cuerpo-tecnico`
(el entrenador del equipo; `CuerpoTecnicoResponse` propio). `GET /jugadores` admite el filtro opcional
`?equipoId=`. **RN-02:** al cambiar `equipoId` en un `PUT` el jugador se mueve, no se duplica (y el dorsal debe
seguir libre en el equipo destino → 409 `DORSAL_DUPLICADO`). **RN-03:** el entrenador no puede estar asignado a
otro equipo → 409 (código `RN-03`), también al actualizar excluyendo el propio equipo. Equipo: único (liga,
nombre) → 409; `anioFundacion` no futuro → 422 `ANIO_FUNDACION_FUTURO`. Jugador: `fechaNacimiento` en el pasado
(→ 422 `FECHA_NACIMIENTO_FUTURA`), dorsal 1–99 (→ 400), único (equipo, dorsal) → 409. Crea los verificadores de §6
que le tocan y llama al punto de extensión en `EquipoService.eliminar` y `JugadorService.eliminar`.

**Fase 4 — Partido y calendario** (F2, F8; RN-04 a RN-07, RN-12 parcial): `/partidos` con `POST`,
`GET` (calendario con filtros opcionales `ligaId`, `jornada`, `equipoId` —local o visitante— y `estadioId`,
paginado), `GET /{id}`, `PUT /{id}`, `DELETE /{id}` y `POST /{id}/iniciar` (PROGRAMADO → EN_JUEGO; otro estado →
409 `PARTIDO_ESTADO_INVALIDO`). `PartidoRequest { ligaId, jornada, fechaHora, equipoLocalId, equipoVisitanteId,
estadioId, arbitroId }`. `PartidoResponse { id, liga, jornada, fechaHora, equipoLocal, equipoVisitante, estadio,
arbitro, golesLocal, golesVisitante, estado }` (relaciones como `ResumenResponse`). **RN-04** local ≠ visitante →
422. **RN-05** un equipo no juega dos partidos en la misma jornada de la misma liga (local o visitante; al
editar se excluye el propio partido) → 409. **RN-06/RN-07** estadio y árbitro sin dos partidos a la misma
fecha y hora → 409: **toda la comparación vive en UN método privado** (por ejemplo `chocaEnHorario(...)`) con una
constante `VENTANA_SOLAPE = Duration.ZERO` (literal del documento: fecha y hora exactas); cambiarla a ~2 h debe
ser tocar solo esa constante, y debe haber una prueba que lo demuestre con una constante de prueba/sobrecarga.
**RN-12 (parte del partido):** `PUT`/`DELETE` de un partido `FINALIZADO` → 409 (código `RN-12`); si está
`EN_JUEGO` también se rechazan, con código `PARTIDO_EN_JUEGO` (supuesto a confirmar: lo reportas). Crea los
verificadores de §6 que le tocan.

**Fase 5 — Goles, tarjetas y cierre** (F3; RN-08 a RN-12): `POST/GET /partidos/{partidoId}/goles` y
`DELETE /partidos/{partidoId}/goles/{golId}`; lo mismo para `/tarjetas`; `POST /partidos/{partidoId}/finalizar`.
Cuerpos: `GolRequest { jugadorId, minuto (1–130), tipo }`, `TarjetaRequest { jugadorId, tipo, minuto (1–130) }`,
`FinalizarPartidoRequest { golesLocal, golesVisitante }` (≥ 0). Clases propias (no uses nada de la Fase 4 salvo
la entidad `Partido`, `EstadoPartido` y `PartidoRepository` base): `GolService`, `TarjetaService`,
`CierrePartidoService` (la operación `finalizar`; el plan la llama `PartidoService.finalizar`, pero ese archivo
es de la Fase 4 y aquí vive en su propia clase), `SuspensionServiceImpl`, y para la respuesta de finalizar
`ResultadoPartidoResponse { partidoId, ligaId, golesLocal, golesVisitante, estado }`. Reglas: goles y tarjetas
solo con el partido `EN_JUEGO` (otro estado → 409 `PARTIDO_NO_EN_JUEGO`; si es `FINALIZADO`, código `RN-12`);
**RN-08** el jugador debe pertenecer a uno de los dos equipos del partido → 422; **RN-09** la 2.ª amarilla a un
jugador en un partido crea la ROJA en el mismo minuto y en la misma transacción (y se rechazan nuevas tarjetas
a un jugador ya expulsado en ese partido → 422 `TARJETA_JUGADOR_EXPULSADO`); **RN-10** un suspendido
(`SuspensionService`, definición en su Javadoc) no puede tener goles ni tarjetas en el partido → 422;
**RN-11** al finalizar, `golesLocal` = goles NORMAL/PENAL de jugadores locales + AUTOGOL de jugadores visitantes
(y análogo para el visitante); si el cuerpo no coincide → 422; **RN-12** un partido `FINALIZADO` no admite
cambios ni sus goles/tarjetas. `finalizar` solo desde `EN_JUEGO`. `CierrePartidoService.finalizar` lleva
`@CacheEvict(cacheNames = {NombresCache.TABLA_POSICIONES, NombresCache.GOLEADORES}, key = "#result.ligaId()")`.
Crea `JugadorConEventosVerificador` (§6).

**Fase 6 — Consultas derivadas y caché** (F4 a F7; RN-01; D-3): `GET /ligas/{id}/tabla-posiciones`
(PJ, PG, PE, PP, GF, GC, DG, Pts; orden: puntos → diferencia de gol → goles a favor; **RN-01**: victoria 3,
empate 1, derrota 0; solo partidos `FINALIZADO`), `GET /ligas/{id}/goleadores` (NORMAL y PENAL suman, AUTOGOL
no; solo partidos `FINALIZADO`), `GET /ligas/{id}/sanciones` (jugadores con tarjetas, con conteo de amarillas
y rojas), `GET /ligas/{id}/sanciones/suspendidos?jornada=` (usa `SuspensionService.suspendidosEnPartido` sobre
los partidos de esa jornada) y `GET /equipos/{id}/partidos` (historial del equipo, paginado). Clases nuevas en
controladores propios (`EstadisticasLigaController`, `HistorialEquipoController`), servicios propios
(`TablaPosicionesService`, `GoleadoresService`, `SancionService`, `HistorialEquipoService`) y repositorios
propios. Cálculo en el servicio con *streams* sobre los partidos finalizados (opción (b) del plan). Caché:
`@Cacheable(cacheNames = NombresCache.TABLA_POSICIONES, key = "#ligaId")` y lo propio con `GOLEADORES`;
`RedisCacheConfig` (`@EnableCaching`, TTL 10 min, prefijo de claves `liga:`, serialización **JSON** —no la
serialización Java—, se cachean DTO de respuesta, nunca entidades) y un `CacheErrorHandler` que registra el
fallo en WARN y deja pasar a la base de datos si Redis cae. Una llamada dentro de la misma clase no pasa por el
proxy: anota el servicio y llámalo desde el controlador. 404 si la liga no existe. Verifica los nombres de clase
del serializador JSON de Spring Data Redis 4 / Jackson 3 en los `*-sources.jar` de `C:\Users\Julia\.m2`
(la API cambió respecto a Boot 3).

## 8. Pruebas (obligatorias en cada fase)

- Nombre del test con el código de la regla (`RN-05`, `EST-01`), con **prueba positiva y negativa** por regla.
  Servicios con Mockito; mappers sin Spring; verificadores con Mockito.
- **Controlador (`*ControllerTest`)**: `@WebMvcTest(XController.class)` con el mismo bloque de `@Import`,
  `@ActiveProfiles` y `@MockitoBean` que `config/SecurityConfigTest` y `controller/AuthControllerTest`; tokens
  con `JwtService.generarToken(UsuariosDePruebaConfig.ROOT, Rol.ADMIN)` / `...ANA, Rol.USER`. Matriz por
  endpoint: sin token → 401, `USER` en escritura → 403, `ADMIN` → 2xx, `USER`/`ADMIN` en `GET` → 200; cuerpo
  inválido → 400; JSON de error con `codigo`. Comprueba también `Location` en el 201.
- **Integración (`*IT`, requiere Docker):** como máximo **una clase por fase**, que **extienda
  `support.BaseIT`** (misma configuración, para reutilizar el contexto de Spring). Datos únicos con
  `unico("...")`, sin asumir tablas vacías. Debe cubrir al menos: creación real vía HTTP con un ADMIN real
  (`authAdmin()`), una violación de unicidad de la base de datos y el borrado con dependientes. Estos `*IT` y las
  migraciones **no los puedes ejecutar**: escríbelos con cuidado y decláralos "sin ejecutar" en el informe.
- No uses `Thread.sleep` ni dependas del reloj salvo con `Instant` fijos; no dependas del orden de las pruebas.

## 9. Herramienta de verificación (sin Maven, sin Docker)

Compila `src/` del proyecto + los borradores que indiques (superpuestos en orden, el último gana) en un
espacio temporal fuera del proyecto, con JDK 21, Lombok y MapStruct. Con `-EjecutarPruebas` corre tus clases
`*Test` (no los `*IT`) con JUnit; **solo una ejecución de pruebas a la vez en toda la máquina** (hay un
semáforo: si esperas turno, es normal) porque cada contexto de Spring consume mucha memoria y el equipo tiene
poca. Una ejecución con Spring tarda de 1 a 4 minutos: ejecuta primero solo compilación (rápida), y las
pruebas por subconjuntos (`-Clases`) hasta que pasen.

```powershell
# Solo compilar (usa siempre primero este):
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Julia\AppData\Local\Temp\claude\D--Proyect-Liga-futbol\d4f732da-72d4-41ff-9cbc-50bc630e7805\scratchpad\herramientas\compilar-borrador.ps1" -Nombre faseN -Borradores "D:\Proyect_Liga_futbol\_borrador_dominio","D:\Proyect_Liga_futbol\_borrador_faseN"

# Compilar y ejecutar todas tus pruebas *Test, o solo algunas (nombres completos):
... -EjecutarPruebas
... -EjecutarPruebas -Clases co.edu.uniajc.liga.service.impl.EquipoServiceImplTest,co.edu.uniajc.liga.controller.EquipoControllerTest
```

Usa `-Nombre` distinto para cada fase (`fase2`, `fase3`...). Para las fases 4 a 6, superpón solo
`_borrador_dominio` y tu propia carpeta (no necesitas los borradores de las otras fases: ya tienes lo que usas
en el dominio común). Sobre la salida: `compilacion OK` es lo mínimo; `PRUEBAS OK` es lo que buscas.

## 10. Informe final (breve y concreto)

1. Archivos creados y su ruta destino; cuáles reemplazan a archivos del dominio base.
2. **Evidencia:** resultado literal de la compilación y de las pruebas ejecutadas (cuántas pasaron), y lista de
   lo que NO se pudo ejecutar (los `*IT`, las migraciones).
3. Decisiones no triviales con su porqué en una línea; reglas implementadas con su ubicación y su prueba.
4. Propuestas y supuestos (reglas que crees que faltan, cambios que necesitas en el dominio común).
5. Riesgos de integración o de comportamiento, de mayor a menor probabilidad de romper.

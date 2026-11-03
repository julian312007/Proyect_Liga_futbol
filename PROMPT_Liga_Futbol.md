# Prompt de trabajo — Sistema de Gestión de una Liga de Fútbol

> **Cómo usarlo:** pega este archivo completo como primer mensaje en tu agente de código, o guárdalo como `CLAUDE.md` / `PROMPT.md` en la raíz del repositorio. Si ya existe un `ESTATUS.md`, el agente debe leerlo primero y continuar desde ahí.

---

## 0. Decisiones confirmadas el 2026-11-02 (prevalecen sobre el resto de este documento)

- **Base técnica:** Java 21, Spring Boot 4.1.x (la línea del repositorio del profesor), Maven con wrapper (`mvnw`). Paquete base `co.edu.uniajc.liga`.
- **Infraestructura local:** `docker-compose` con PostgreSQL 17 y Redis 7; configuración por variables de entorno en un `.env` (plantilla: `.env.example`).
- **JWT:** `jjwt` con `jjwt-gson` (Boot 4 usa Jackson 3 y `jjwt-jackson` depende de Jackson 2).
- **Rutas y nombres:** prefijo `/api/v1/...` (en este documento aparece como `/api/...`) y columnas de id con el patrón `id_<entidad>`.
- **Dependencias:** se incluyen las de las secciones 4.1, 4.2 y 4.3 (MapStruct, Flyway, springdoc 3.x, Testcontainers).
- **Swagger:** un grupo por módulo (autenticación, catálogos, equipos y jugadores, partidos, estadísticas), declarados en `application.yaml`.
- **Autenticación con Google (indicada por el equipo el 2026-11-02):** el inicio de sesión se hace **con Google** (Google Sign-In), no con usuario y contraseña. Esto sustituye el registro/login con contraseña de la sección 4.4, de D-1 y de A1/A2. El JWT propio y los roles `ADMIN`/`USER` se mantienen. El diseño detallado (ID token de Google → `POST /api/v1/auth/google` → JWT propio) está en `ESTATUS.md`, sección 8. Confirmado el 2026-11-02: **solo Google**, sin contraseña de respaldo; el mecanismo exacto sigue como propuesta de trabajo.
- **Repositorio del profesor** (https://github.com/fabiancvuniajc/apiUniversitaria): se toma **como ejemplo de estilo, no como alcance a replicar** (D-5). Es un ejemplo mínimo, sin seguridad ni caché. La matriz de comparación está en el Anexo A de `ESTATUS.md`.

---

## 1. Rol y objetivo

Actúa como **Desarrollador de Software Senior y Analista de Código experto en Java y Spring Boot**. Vas a construir, conmigo, el backend de un *Sistema de Gestión de una Liga de Fútbol* (proyecto de Santiago Panesso y Julián Valencia, Ingeniería en Sistemas, Universidad Antonio José Camacho, Cali), organizado en capas: **Entidades, Repositorios, DTOs, Mappers, Servicios y Controladores**.

**Objetivo:**
- Implementar las 9 entidades del documento, sus 8 funcionalidades y sus 12 reglas de negocio.
- Añadir la entidad `Usuario` para autenticación (la propuesta permite ampliaciones: *"cualquier ampliación adicional se definirá en el transcurso del curso"*).
- Cumplir los requisitos del docente (sección 2) y cubrir el alcance del repositorio de referencia del profesor (sección 9), **pero con una estructura mejor organizada**.
- Mantener un `ESTATUS.md` honesto de lo que se realizó y lo que no.

Fuente de verdad funcional: la propuesta `Propuesta_Proyecto_Liga_Futbol.docx` (si la tienes disponible). Este prompt la resume; si ambos se contradicen o algo es ambiguo, **pregunta**.

---

## 2. Requisitos del docente (criterios de evaluación)

| ID | Requisito | Cómo se cumple | Evidencia mínima |
|---|---|---|---|
| **D-1** | Controlador de autenticación con **registro** y **login** (obligatorios); ver/editar perfil (opcional) | `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`; opcional `GET/PUT /api/auth/me` | Registro `201`; login devuelve token; credenciales inválidas `401` |
| **D-2** | Seguridad: manejo de **tokens** y definición de **roles**; reglas de acceso en `SecurityConfig` o con `@PreAuthorize` | JWT sin sesión; roles `ADMIN` y `USER`; `SecurityConfig` (autenticación) + `@PreAuthorize` (rol) | Sin token `401`; `USER` en endpoint de escritura `403`; `ADMIN` `2xx` |
| **D-3** | Al menos **una caché funcional** (Redis con `RedisTemplate` o con `@Cacheable` / `RedisCacheConfig`) | `@Cacheable`/`@CacheEvict` + `RedisCacheConfig` en tabla de posiciones y goleadores (sección 4.5) | La 2.ª consulta no toca la BD; `finalizar` invalida la caché |
| **D-4** | **Segundo controlador** sobre una entidad **independiente** (se registra sin depender de otras tablas), con lógica de negocio (validaciones) en el servicio y protección por roles | `EstadioController` + `EstadioService` (sección 6.1) | Cada validación con prueba; rutas protegidas por rol |
| **D-5** | Replicar el alcance del repositorio del profesor (entidades, DTO, controladores, dependencias) con buena estructura | Inventario y matriz de comparación (sección 9) | Matriz sin faltantes |
| **D-6** | Entrega: enlace al repositorio, o ZIP tras ejecutar `clean` | Sección 13 | ZIP liviano, sin `target/` |

---

## 3. Reglas de trabajo (obligatorias)

1. **Contexto antes de documentar.** Antes de generar documentación, Javadoc, README, comentarios explicativos o diagramas sobre cualquier bloque de código o arquitectura, verifica que tienes el contexto necesario: flujo de negocio, dependencias, variables de entorno, contratos de API y modelos de datos. Si falta una pieza clave, **detente y pídemela explícitamente** antes de continuar. No rellenes huecos en silencio.
2. **Criterio técnico, no obediencia ciega.** Evalúa mis decisiones de diseño, algoritmos, patrones de arquitectura y uso de librerías. Si detectas riesgos de rendimiento o seguridad, acoplamiento excesivo o una alternativa mejor, indícalo con **pros, contras y una recomendación fundamentada** antes de implementar. No refactorices a ciegas.
3. **Justifica los cambios.** Toda decisión no trivial lleva su porqué en una línea (en el resumen de fase o en `ESTATUS.md`).
4. **Trabaja por fases (sección 8).** Al terminar cada fase: compila, corre las pruebas, actualiza `ESTATUS.md`, muéstrame un resumen corto y **espera mi confirmación** antes de pasar a la siguiente (salvo que yo diga "continúa sin pausas").
5. **No inventes y no declares éxito sin evidencia.** Algo está "hecho" solo si compila y tiene una prueba que lo respalde.
6. **Sin acciones irreversibles sin permiso:** no hagas commits, push ni borres archivos a menos que yo lo pida.
7. **Idioma:** dominio en español (`Equipo`, `Jugador`, `Partido`… como en el documento); sufijos técnicos en inglés estándar (`Service`, `Repository`, `Controller`, `Mapper`); Javadoc, comentarios y mensajes de error en español.
8. **No copies código del repositorio del profesor.** Replica su alcance funcional y reescríbelo con nuestra estructura.

---

## 4. Stack y arquitectura

**Base:** Java 21 (LTS), Maven y la última versión estable de Spring Boot compatible con el JDK (confirmar JDK instalado en la Fase 0). Datos: **PostgreSQL**. Caché: **Redis**.

### 4.1 Dependencias requeridas por el equipo (Spring Initializr)

| Dependencia | Uso en el proyecto |
|---|---|
| **Spring Web** | API REST (controladores) |
| **PostgreSQL Driver** | Conexión a PostgreSQL |
| **Validation** | Jakarta Bean Validation en los DTO de entrada |
| **Lombok** | Solo en entidades (`@Getter/@Setter`); **evita `@Data`** en entidades JPA: rompe `equals/hashCode` y `toString` con relaciones LAZY. Los DTO son `record` |
| **Spring Boot DevTools** | Solo desarrollo (reinicio automático); fuera del artefacto de producción |
| **Spring Security** | Autenticación y autorización (sección 4.4) |

### 4.2 Dependencias necesarias por los requisitos (no estaban en la lista inicial)

La lista anterior **no alcanza**. Propón estas adiciones y espera mi decisión antes de añadirlas; contrástalas con el `pom.xml` del profesor (sección 9):

| Dependencia | Por qué | Si se rechaza |
|---|---|---|
| **Spring Data JPA** | El driver de PostgreSQL solo conecta; sin JPA no hay entidades ni repositorios | Spring JDBC (`JdbcTemplate`): mucho SQL manual y sin repositorios estándar |
| **Spring Data Redis** + **Spring Cache** (`spring-boot-starter-data-redis`, `spring-boot-starter-cache`) | D-3: caché con Redis y `@Cacheable` | `RedisTemplate` manual solo con `starter-data-redis` |
| **Librería JWT:** `jjwt` (api, impl, jackson) **o** `spring-boot-starter-oauth2-resource-server` | D-2: firmar y validar tokens | — |
| `spring-security-test` *(scope test)* | Probar endpoints protegidos (`@WithMockUser`) | Pruebas manuales de seguridad |

Sobre la librería JWT: `oauth2-resource-server` es la opción mantenida por Spring (validación con Nimbus, menos código propio); `jjwt` requiere un filtro propio pero es más fácil de leer. **Recomendación:** usa la que use el repositorio del profesor; si no hay preferencia, `jjwt` por claridad didáctica.

### 4.3 Dependencias recomendadas (opcionales)

| Dependencia | Por qué | Si se rechaza |
|---|---|---|
| **MapStruct** | Mappers generados en compilación. Con Lombok exige `lombok-mapstruct-binding` y el orden correcto de *annotation processors* | Mappers manuales `@Component`: sin dependencia extra, más código |
| **Flyway** (`flyway-core`, más el módulo de PostgreSQL si la versión lo pide) | Esquema versionado y reproducible, con `ddl-auto=validate` | `ddl-auto=update`: rápido, pero sin control de versiones |
| `springdoc-openapi` (Swagger UI) | Documentar y probar la API (con botón *Authorize* para el token) | Omitir |
| Testcontainers *(test)* | Pruebas de integración con PostgreSQL y Redis reales | H2 en modo PostgreSQL: más simple, con diferencias de comportamiento |

**Pruebas:** JUnit 5 + Mockito (servicios); `@DataJpaTest` y `@WebMvcTest` para capas.

### 4.4 Seguridad: autenticación JWT y roles (D-1, D-2)

**Modelo:** entidad `Usuario` (sección 5) y enum `Rol { ADMIN, USER }`.

**Registro** — `POST /api/auth/register`
- Entrada `RegistroRequest { username, email, password, nombre }` con validaciones: `username` 3–30 caracteres, `email` válido, `password` mínimo 8 con letra y número.
- `username` y `email` únicos (`409`). Contraseña con **BCrypt**; nunca se devuelve el hash.
- El rol asignado es **siempre `USER`**. El DTO **no** incluye el campo rol: evita escalada de privilegios por *mass assignment*.
- Respuesta `201` con `UsuarioResponse`.

**Login** — `POST /api/auth/login`
- Entrada `LoginRequest { username, password }`; salida `AuthResponse { token, tipo: "Bearer", expiraEn, username, rol }`.
- Credenciales inválidas: `401` con mensaje **genérico** (no revela si el usuario existe).

**Token JWT**
- Firma HS256; secreto en `JWT_SECRET` (≥ 256 bits, variable de entorno); vigencia en `JWT_EXPIRATION_MINUTES` (por defecto 60).
- Claims: `sub` (username), `rol`, `iat`, `exp`. Sin *refresh token* ni lista de revocación (fuera de alcance); el *logout* lo hace el cliente descartando el token, y debe quedar documentado.

**Componentes (paquete `security/` y `config/`):** `JwtService` (generar y validar), `JwtAuthenticationFilter` (`OncePerRequestFilter`), `UserDetailsServiceImpl`, `SecurityConfig` (`SecurityFilterChain`, `AuthenticationManager`, `PasswordEncoder`), `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` (devuelven `401`/`403` con el mismo formato `ApiError`).

**Reglas de acceso en dos niveles, sin duplicarse:**

| Nivel | Dónde | Qué decide |
|---|---|---|
| Autenticación | `SecurityConfig` | Públicos: `POST /api/auth/register`, `POST /api/auth/login`, Swagger/OpenAPI y `/error`. Todo lo demás `authenticated()`. Sin sesión (`STATELESS`); CSRF deshabilitado **solo porque** no hay sesión ni cookies (déjalo comentado) |
| Autorización | `@PreAuthorize` en los métodos del controlador (con `@EnableMethodSecurity`) | `hasRole('ADMIN')` en `POST/PUT/DELETE` y acciones (`iniciar`, `finalizar`, goles, tarjetas); `hasAnyRole('USER','ADMIN')` en `GET` |

**Administrador inicial:** al arrancar, un `ApplicationRunner` idempotente crea un `ADMIN` si no existe, con `LIGA_ADMIN_USER`, `LIGA_ADMIN_EMAIL` y `LIGA_ADMIN_PASSWORD` desde variables de entorno. Nunca contraseñas en el repositorio.

**Opcionales (D-1):** `GET /api/auth/me`, `PUT /api/auth/me` (nombre y email; cambiar la contraseña exige la actual) y `PUT /api/usuarios/{id}/rol` (solo `ADMIN`).

**Riesgos que debes señalarme:** secreto débil o expuesto; token guardado en `localStorage` si hay frontend (XSS); vigencia demasiado larga; fuerza bruta en el login (el *rate limiting* queda fuera de alcance); HTTPS obligatorio fuera de desarrollo.

### 4.5 Caché con Redis (D-3)

- **Enfoque:** anotaciones (`@Cacheable`, `@CacheEvict`) + `RedisCacheConfig` con `RedisCacheManager`: declarativo y con menos código. `RedisTemplate` manual queda como alternativa.
- **Qué se cachea:** `tablaPosiciones` y `goleadores`, con clave `ligaId`. Son consultas agregadas y de lectura frecuente. **Ambas cuentan solo partidos `FINALIZADO`**, lo que hace simple invalidarlas. Opcional: `estadios` por id.
- **Invalidación:** `@CacheEvict` en `PartidoService.finalizar` (clave = liga del partido) y al eliminar una liga. Como un partido finalizado es inmutable (RN-12), no hay otro camino por el que los resultados cambien.
- **Configuración:** TTL por caché (10 min por defecto) como respaldo ante invalidaciones perdidas; serialización **JSON**, no la serialización Java; se cachean **DTO de respuesta**, nunca entidades JPA (relaciones LAZY); prefijo de claves.
- **Resiliencia:** un `CacheErrorHandler` que registra el fallo (`WARN`) y consulta la BD si Redis cae. Pro: la API sigue viva. Contra: puede ocultar el problema, por eso se registra. Recomendado.
- **Trampas:** una llamada dentro de la misma clase no pasa por el proxy y la anotación no aplica; anota el **servicio**, no el controlador ni el repositorio.
- **Pruebas:** perfil de pruebas con `spring.cache.type=simple` para la lógica, y una prueba de caché que verifique que la 2.ª llamada no consulta el repositorio (`verify(repo, times(1))`) y que `finalizar` la invalida.
- **Infraestructura:** `docker-compose` con `postgres` y `redis`; variables `REDIS_HOST` y `REDIS_PORT`.

### 4.6 Estructura de paquetes

**Paquete base propuesto:** `co.edu.uniajc.liga` (confirmar en la Fase 0).

```
co.edu.uniajc.liga
├── config/            SecurityConfig, RedisCacheConfig, OpenApiConfig, AdminInicializador
├── security/          JwtService, JwtAuthenticationFilter, UserDetailsServiceImpl, handlers 401/403
├── entity/            entidades JPA + enums
├── repository/        interfaces Spring Data
├── dto/
│   ├── request/       XRequest (entrada, con validaciones)
│   └── response/      XResponse (salida)
├── mapper/            interfaces MapStruct (o clases @Component)
├── service/           interfaces de servicio
│   └── impl/          implementaciones (reglas de negocio, @Cacheable)
├── controller/        AuthController, EstadioController, … (delgados, @PreAuthorize)
└── exception/         excepciones de dominio + GlobalExceptionHandler + ApiError
```

### Responsabilidad de cada capa

| Capa | Debe | No debe |
|---|---|---|
| **Entity** | Mapear tablas; relaciones `LAZY`; restricciones (`unique`, `nullable`) alineadas con la BD | Salir del servicio hacia el controlador; contener reglas complejas |
| **Repository** | Consultas derivadas o `@Query` JPQL; evitar N+1 (`@EntityGraph` / `join fetch`) | Contener lógica de negocio |
| **DTO** | `XRequest` con Bean Validation y relaciones por id (`estadioLocalId`); `XResponse` con resúmenes `{id, nombre}` de las relaciones (evita ciclos) | Exponer entidades; incluir campos sensibles (hash, rol en el registro) |
| **Mapper** | `toEntity`, `toResponse`, `updateEntity(@MappingTarget)`; ignorar relaciones | Acceder a repositorios (el servicio resuelve las relaciones por id) |
| **Service** | **Todas** las reglas de negocio; `@Transactional` (y `readOnly = true` en consultas); `@Cacheable`/`@CacheEvict`; lanzar excepciones de dominio; único que usa repositorios y mappers; inyección por constructor | Conocer HTTP |
| **Controller** | `@Valid`, `@PreAuthorize`, delegar al servicio, devolver DTOs; `201` + `Location` al crear, `204` al borrar | Lógica de negocio; usar repositorios |

---

## 5. Modelo de dominio

### 5.1 Entidades (9 del documento + `Usuario`)

| Entidad | Atributos | Validaciones / restricciones |
|---|---|---|
| **Liga** | id, nombre, temporada, fechaInicio, fechaFin | nombre y temporada obligatorios; fechaFin > fechaInicio; único (nombre, temporada) |
| **Estadio** | id, nombre, ciudad, capacidad | capacidad > 0 |
| **Entrenador** | id, nombre, nacionalidad | nombre obligatorio |
| **Arbitro** | id, nombre, nacionalidad, aniosExperiencia | aniosExperiencia ≥ 0 |
| **Equipo** | id, nombre, ciudad, anioFundacion, estadioLocal, entrenador, liga | único (liga, nombre); anioFundacion no futuro |
| **Jugador** | id, nombre, fechaNacimiento, posicion, dorsal, equipo | fechaNacimiento en el pasado; dorsal 1–99; único (equipo, dorsal) *(supuesto)* |
| **Partido** | id, liga, jornada, fechaHora, equipoLocal, equipoVisitante, estadio, arbitro, golesLocal, golesVisitante, estado | jornada ≥ 1; goles ≥ 0; CHECK `equipo_local_id <> equipo_visitante_id` |
| **Gol** | id, partido, jugador, minuto, tipo | minuto 1–130 *(supuesto)* |
| **Tarjeta** | id, partido, jugador, tipo, minuto | minuto 1–130 *(supuesto)* |
| **Usuario** *(ampliación)* | id, username, email, passwordHash, nombre, rol, activo, fechaCreacion | username único 3–30; email único y válido; hash BCrypt; rol por defecto `USER` |

**Enums:** `PosicionJugador` (PORTERO, DEFENSA, MEDIOCAMPISTA, DELANTERO) · `TipoGol` (NORMAL, PENAL, AUTOGOL) · `TipoTarjeta` (AMARILLA, ROJA) · `EstadoPartido` (PROGRAMADO, EN_JUEGO, FINALIZADO) · `Rol` (ADMIN, USER).

### 5.2 Relaciones

- Liga 1—N Equipo y Liga 1—N Partido.
- Equipo 1—N Jugador.
- Equipo 1—1 Entrenador: la FK única vive en `equipo.entrenador_id`; `Entrenador` es el lado inverso.
- Equipo N—1 Estadio (estadio local; un estadio puede ser local de varios equipos).
- Partido N—1 Equipo (local), N—1 Equipo (visitante), N—1 Estadio, N—1 Arbitro.
- Partido 1—N Gol y Partido 1—N Tarjeta; cada Gol y Tarjeta N—1 Jugador.
- `Usuario` no se relaciona con las demás entidades.

**Entidades independientes** (se registran sin que exista otra tabla): `Estadio`, `Arbitro`, `Entrenador`, `Liga` y `Usuario`. Esto es lo que habilita el requisito D-4.

### 5.3 Ciclo de vida del partido

`PROGRAMADO → EN_JUEGO → FINALIZADO`. Goles y tarjetas solo se registran con el partido `EN_JUEGO`. `FINALIZADO` es terminal (RN-12).

---

## 6. Reglas de negocio

### 6.0 Reglas del documento (12)

Cada regla debe tener al menos **una prueba positiva y una negativa**, y el nombre del test debe incluir su código (`RN-05`). Las excepciones llevan el código de la regla en `ApiError.codigo`.

| Código | Regla | Dónde se valida | HTTP |
|---|---|---|---|
| RN-01 | Victoria = 3 puntos, empate = 1, derrota = 0 | `TablaPosicionesService` | — |
| RN-02 | Un jugador pertenece a un solo equipo a la vez | Modelo (`Jugador.equipo` ManyToOne); al cambiar `equipoId` el jugador se mueve, no se duplica | — |
| RN-03 | Un equipo tiene un único entrenador y un entrenador dirige a un solo equipo | FK única + `EquipoService` verifica que el entrenador no esté asignado a otro equipo | 409 |
| RN-04 | Un equipo no puede enfrentarse a sí mismo | `PartidoService` + CHECK en BD | 422 |
| RN-05 | Un equipo no juega más de un partido en la misma jornada (misma liga) | `PartidoService`: consulta si el equipo ya participa (local o visitante) en esa liga y jornada, excluyendo el propio partido al editar | 409 |
| RN-06 | Un estadio no puede tener dos partidos a la misma fecha y hora | `PartidoService` + índice único `(estadio_id, fecha_hora)` | 409 |
| RN-07 | Un árbitro no puede dirigir dos partidos a la misma fecha y hora | `PartidoService` + índice único `(arbitro_id, fecha_hora)` | 409 |
| RN-08 | Un gol solo lo registra un jugador de alguno de los dos equipos del partido | `GolService` | 422 |
| RN-09 | Dos amarillas a un jugador en un mismo partido generan una roja | `TarjetaService`: al registrar la 2.ª amarilla crea la ROJA en el mismo minuto, en la misma transacción | — |
| RN-10 | Un jugador expulsado no juega el siguiente partido de su equipo | `GolService`/`TarjetaService` rechazan eventos de un suspendido; `SancionService` lista suspendidos. **Definición:** un jugador está suspendido en el partido P si recibió ROJA (directa o por doble amarilla) en el partido inmediatamente anterior de su equipo (por `fechaHora`, misma liga) | 422 |
| RN-11 | El marcador debe coincidir con los goles registrados | `PartidoService.finalizar`: `golesLocal` = goles NORMAL/PENAL de jugadores locales + AUTOGOL de jugadores visitantes (y análogo para el visitante) | 422 |
| RN-12 | Un partido finalizado no puede modificarse ni eliminarse (tampoco sus goles y tarjetas) | `PartidoService`, `GolService`, `TarjetaService` | 409 |

**Mapa de errores:** `400` validación del cuerpo (Bean Validation) · `401` sin autenticar o credenciales inválidas · `403` sin el rol requerido · `404` recurso inexistente · `409` conflicto (unicidad, choques de horario, estado, dependencias) · `422` regla de negocio incumplida.
**Formato:** `ApiError { timestamp, status, codigo, mensaje, ruta, detalles[] }`, manejado por un único `@RestControllerAdvice`. Traduce `DataIntegrityViolationException` a `409`.

### 6.1 Controlador independiente de referencia: `Estadio` (D-4)

`EstadioController` + `EstadioService` deben **evidenciar** lógica de negocio y protección por rol. Reglas propuestas (supuestos, confirmar):

| Código | Regla | Dónde | HTTP |
|---|---|---|---|
| EST-01 | `nombre` se normaliza (sin espacios sobrantes) y es único por ciudad, sin distinguir mayúsculas | `EstadioService` (crear y actualizar) + índice único | 409 |
| EST-02 | Un estadio no se elimina si es estadio local de algún equipo o tiene partidos | `EstadioService.eliminar` | 409 |
| EST-03 | `capacidad` > 0 y `ciudad` obligatoria | `EstadioRequest` (Bean Validation) | 400 |

**Acceso:** `GET` con `hasAnyRole('USER','ADMIN')`; `POST/PUT/DELETE` con `hasRole('ADMIN')`. Pruebas: cada regla (positiva y negativa) y la matriz de roles (`401`, `403`, `2xx`).
**Alternativas** si el repositorio del profesor sugiere otra: `Arbitro` o `Entrenador`.

---

## 7. Funcionalidades y endpoints

### 7.1 Funcionalidades del documento (8)

| # | Funcionalidad | Endpoints | Acceso |
|---|---|---|---|
| F1 | CRUD de ligas, equipos, jugadores, entrenadores, estadios y árbitros | `/api/{ligas\|equipos\|jugadores\|entrenadores\|estadios\|arbitros}`: `GET` (paginado), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | `GET` USER/ADMIN; resto ADMIN |
| F2 | Programar jornadas y partidos | `POST/PUT/DELETE /api/partidos` (equipos, estadio, árbitro, fechaHora, jornada) | ADMIN |
| F3 | Registrar resultado, goles y tarjetas | `POST /api/partidos/{id}/iniciar` · `POST/GET/DELETE /api/partidos/{id}/goles` · `POST/GET/DELETE /api/partidos/{id}/tarjetas` · `POST /api/partidos/{id}/finalizar` (body: `golesLocal`, `golesVisitante`) | `GET` USER/ADMIN; resto ADMIN |
| F4 | Tabla de posiciones *(en caché)* | `GET /api/ligas/{id}/tabla-posiciones` → PJ, PG, PE, PP, GF, GC, DG, Pts | USER/ADMIN |
| F5 | Tabla de goleadores *(en caché)* | `GET /api/ligas/{id}/goleadores` (NORMAL y PENAL suman; AUTOGOL no) | USER/ADMIN |
| F6 | Plantel, cuerpo técnico e historial de un equipo | `GET /api/equipos/{id}/plantel` · `/cuerpo-tecnico` · `/partidos` | USER/ADMIN |
| F7 | Sanciones | `GET /api/ligas/{id}/sanciones` (jugadores con tarjetas) · `GET /api/ligas/{id}/sanciones/suspendidos?jornada=` | USER/ADMIN |
| F8 | Calendario por jornada, equipo o estadio | `GET /api/partidos?ligaId=&jornada=&equipoId=&estadioId=` | USER/ADMIN |

Orden de la tabla de posiciones: puntos → diferencia de gol → goles a favor. Tabla y goleadores cuentan solo partidos `FINALIZADO`.

### 7.2 Autenticación (requisito D-1)

| # | Funcionalidad | Endpoint | Acceso |
|---|---|---|---|
| A1 | Registro de usuarios | `POST /api/auth/register` | Público |
| A2 | Inicio de sesión | `POST /api/auth/login` | Público |
| A3 | Ver y editar perfil *(opcional)* | `GET/PUT /api/auth/me` | Autenticado |
| A4 | Cambiar rol *(opcional)* | `PUT /api/usuarios/{id}/rol` | ADMIN |

---

## 8. Plan de desarrollo por fases

Cada recurso se entrega como un **corte vertical completo**: Entity → Repository → DTOs → Mapper → Service → Controller → pruebas. El orden respeta las dependencias de claves foráneas.

| Fase | Contenido | Cubre | Criterio de terminado |
|---|---|---|---|
| **0. Preparación** | Preguntas (sección 10); análisis del repo del profesor (sección 9); esqueleto con las dependencias confirmadas; `docker-compose` con PostgreSQL y Redis; Flyway base; `GlobalExceptionHandler` + `ApiError`; OpenAPI | D-5 | La app arranca y conecta a PostgreSQL y Redis; `mvn verify` pasa; matriz de comparación entregada |
| **1. Autenticación y seguridad** | `Usuario`, `Rol`, JWT, `SecurityConfig`, handlers 401/403, administrador inicial, `AuthController` (registro, login, perfil opcional) | D-1, D-2 | Registro y login funcionan; las rutas responden `401`/`403`/`2xx` según token y rol |
| **2. Catálogos** | **Estadio primero** (controlador independiente de referencia), luego Arbitro, Entrenador, Liga | F1 (parcial), D-4 | CRUD + reglas EST-01 a EST-03 + `@PreAuthorize` + pruebas |
| **3. Equipo y Jugador** | Relaciones con liga, estadio y entrenador | F1, F6 (plantel y cuerpo técnico) · RN-02, RN-03 | Pruebas de las reglas |
| **4. Partido y calendario** | Programación, validaciones de choques, `iniciar` | F2, F8 · RN-04 a RN-07 | Pruebas de cada choque (positivo y negativo) |
| **5. Goles, tarjetas y cierre** | Eventos, roja automática, suspensión, `finalizar` | F3 · RN-08 a RN-12 | Pruebas de cada regla; un partido finalizado rechaza toda modificación |
| **6. Consultas derivadas y caché** | Tabla de posiciones, goleadores, historial, sanciones; Redis con `@Cacheable`/`@CacheEvict` | F4 a F7 · RN-01 · D-3 | Resultados verificados contra casos calculados a mano; prueba de caché |
| **7. Cierre y entrega** | Escenario de punta a punta (mini-liga de 4 equipos), revisión de seguridad y rendimiento, README (solo con contexto completo, regla 1), `ESTATUS.md` final, `clean` y entrega | Todo · D-6 | `mvn clean verify` en verde; entrega lista (sección 13) |

**Tabla de posiciones y goleadores:** evalúa dos opciones y justifica tu elección: (a) agregación en JPQL/SQL, (b) cálculo en el servicio con streams sobre los partidos finalizados. Recomendación inicial: (b) por claridad y facilidad de prueba; (a) si el rendimiento lo exige. La caché amortigua el costo en ambos casos.

---

## 9. Repositorio del profesor (referencia de alcance, D-5)

**URL:** *(pendiente: pegar aquí el enlace al repositorio de GitHub del profesor)*

El docente quiere que el proyecto se construya como lo hace él: **mismas entidades, DTO, controladores y dependencias**, pero con mejor estructura.

1. Si puedes acceder al repositorio, lee `pom.xml`, `application.properties`/`yml`, entidades, DTOs, controladores, servicios y la configuración de seguridad y caché. Si no puedes, pídeme el enlace o los archivos: **no asumas su contenido**.
2. Entrega una **matriz de comparación** antes de la Fase 1:

| Elemento | En el repo del profesor | En nuestro diseño | Acción (igualar / mejorar / descartar con justificación) |
|---|---|---|---|

   Debe cubrir: dependencias y versiones; entidades y atributos; DTOs; endpoints y métodos HTTP; roles; configuración de seguridad y token; configuración de caché; nombres de tablas y columnas.
3. **No debe faltar nada de su alcance.** Si sus nombres difieren de los nuestros (rutas, roles, tablas, atributos), pregúntame cuál prevalece: el docente podría probar la API con su propia colección.
4. **Antipatrones comunes en proyectos académicos** (verifica si el repositorio los tiene y propón la mejora con pros y contras): exponer entidades en los controladores; lógica de negocio en controladores; inyección por campo con `@Autowired` (usa constructor); secretos o contraseñas en `application.properties`; errores devueltos como `null` o texto suelto en lugar de un manejador global; DTO sin validaciones; entidades con `@Data`; nombres de paquetes o clases inconsistentes; ausencia de pruebas.
5. No copies código literal (regla 8).

---

## 10. Preguntas iniciales (Fase 0)

Antes de escribir código, hazme **en un solo mensaje** las preguntas que realmente falten. Como mínimo, confirma:

- **Repositorio del profesor:** URL y si tienes acceso; si el docente exige nombres concretos (rutas, roles, tablas, paquete).
- Versión de JDK instalada y si usamos Maven (por defecto) o Gradle.
- `groupId` / paquete base (propuesto: `co.edu.uniajc.liga`).
- Si hay Docker disponible (`docker-compose` para PostgreSQL y Redis). Si no, datos de las instancias locales. **Las credenciales van en variables de entorno, nunca en el repositorio.**
- **Dependencias:** si aceptamos las secciones 4.2 y 4.3, en especial **Spring Data JPA** (necesaria), y la librería JWT (`jjwt` u `oauth2-resource-server`). Si los mappers van con MapStruct o manuales.
- **Roles:** `ADMIN` y `USER` (propuesto) o más (por ejemplo un rol para registrar resultados).
- **Controlador independiente (D-4):** `Estadio` (propuesto) u otra entidad.
- **Consultas (`GET`):** exigir autenticación (propuesto) o dejarlas públicas.
- **Entrega:** enlace a GitHub o ZIP; si el repositorio es privado, a quién darle acceso.
- Si ya existe un repositorio Git y qué estrategia de ramas se usa.
- Si se necesita frontend o solo API.
- Si aceptamos los supuestos de la sección 11 o hay que cambiar alguno.

---

## 11. Supuestos de diseño (editables)

| Tema | Supuesto | Alternativa / riesgo |
|---|---|---|
| Autenticación | JWT HS256 sin sesión; entidad `Usuario`; `ADMIN` inicial creado desde variables de entorno | `oauth2-resource-server`; sesiones |
| Roles | `ADMIN` y `USER` | Un tercer rol (por ejemplo, organizador) para registrar resultados |
| Registro | Siempre crea `USER`; el rol no viaja en el DTO | Permitir elegir el rol: riesgo de escalada de privilegios |
| Consultas (`GET`) | Requieren autenticación (`USER` o `ADMIN`) | Públicas: más cómodo para aficionados, pero se muestra menos protección de rutas |
| Cierre de sesión | El cliente descarta el token | Lista de revocación en Redis (`RedisTemplate`) |
| Caché | Anotaciones + Redis; tabla y goleadores solo con partidos finalizados; TTL de 10 min | `RedisTemplate` manual |
| Controlador independiente | `Estadio` | `Arbitro`, `Entrenador` |
| Jornada | Entero dentro de `Partido`; no es una entidad | Entidad `Jornada` si se necesitan fechas por jornada |
| Tabla de posiciones | Se calcula al consultar, desde partidos FINALIZADOS | Persistirla: más rápida, pero puede quedar inconsistente |
| Marcador | Se guarda en `Partido` y se valida contra los goles al finalizar | Derivarlo siempre de los goles (RN-11 sería imposible de violar) |
| Liga–Equipo | 1:N, como dice el documento | N:M si un equipo debe participar en varias temporadas |
| Borrado | `409` si hay datos dependientes; sin cascada | Cascada: riesgo de perder historial |
| Choque de horario (RN-06/07) | Misma fecha y hora **exactas**, literal del documento | Ventana de solape (~2 h): más realista; **recomendado**, decide el equipo. Aísla la lógica en un único método para poder cambiarla |
| Dorsal | Único por equipo | Sin restricción |
| Minutos | 1–130 (incluye tiempo añadido) | Otro rango |
| Tarjetas tras expulsión | Se rechazan nuevas tarjetas a un jugador ya expulsado en ese partido | Permitirlas |
| Goleadores | NORMAL y PENAL suman; AUTOGOL no | — |
| Servicios | Interfaz + implementación (convención académica habitual) | Clase concreta: menos código repetitivo |
| Esquema de BD | Flyway + `validate` | `ddl-auto=update`: rápido, pero sin control de versiones del esquema |
| Paginación | `Pageable` en listados (jugadores, partidos, goles, tarjetas) | Listas completas |

---

## 12. Calidad, seguridad y rendimiento

- **Pruebas:** servicios con Mockito; repositorios y controladores con *slice tests*; un escenario de punta a punta (mini-liga). Cada RN y cada EST con prueba positiva y negativa.
- **Pruebas de autenticación:** registro duplicado `409`; contraseña débil `400`; login correcto y `401`; token ausente, expirado o manipulado `401`; matriz de roles en cada endpoint de escritura (`401` sin token, `403` con `USER`, `2xx` con `ADMIN`).
- **Seguridad:** sin credenciales ni `JWT_SECRET` en el repositorio; el hash nunca sale en una respuesta; mensajes de login genéricos; las respuestas de error no exponen trazas ni SQL; consultas parametrizadas (JPA); DevTools fuera del artefacto de producción.
- **Rendimiento:** evitar N+1; índices en claves foráneas y en `(estadio_id, fecha_hora)`, `(arbitro_id, fecha_hora)`, `(liga_id, jornada)`; paginar listados; `readOnly = true` en consultas; caché en las consultas agregadas.
- **Concurrencia:** los índices únicos son la última defensa de RN-06, RN-07 y EST-01; se valida en el servicio **y** en la BD.

---

## 13. Entrega (D-6)

**Preferida: enlace al repositorio.** Debe contener código, `README.md`, `docker-compose.yml`, `.env.example` (valores ficticios), `.gitignore` (`target/`, `.env`, `.idea/`, `*.iml`, `.vscode/`) y `ESTATUS.md`. Si es privado, concede acceso al docente.

**Si se entrega en ZIP:** el docente pidió ejecutar antes un `clean` para eliminar `target/` y archivos generados pesados.

```bash
./mvnw clean          # o: mvn clean
# comprimir excluyendo target/, .idea/, .env real; revisar el tamaño del ZIP
```

**Lista previa a entregar:**
1. `mvn clean verify` en verde.
2. Sin secretos en el código ni en el historial (revisa `git grep -iE "password|secret|jwt"`).
3. El README explica cómo levantar PostgreSQL y Redis, qué variables definir (`.env.example`) y cómo entrar con el administrador inicial. Se redacta solo con el contexto completo (regla 1).
4. `ESTATUS.md` actualizado.

Recuerda la regla 6: el *push* y los *commits* los hago yo cuando lo pida.

---

## 14. Estatus obligatorio (`ESTATUS.md`)

Mantén `ESTATUS.md` en la raíz del repositorio. Léelo al empezar cada sesión; actualízalo al cerrar cada fase y cuando yo pida "dame el estatus". Si no existe, créalo con esta estructura:

1. Resumen y avance por fase (0–7).
2. **Requisitos del docente D-1 a D-6** (estado y evidencia).
3. Matriz entidad × capa (Entity, Repository, DTO, Mapper, Service, Controller, Pruebas), incluida `Usuario`.
4. Reglas de negocio RN-01…RN-12 y EST-01…EST-03 (estado, ubicación en el código, prueba).
5. Funcionalidades F1…F8 y A1…A4 (estado, endpoint).
6. **Qué se realizó.**
7. **Qué NO se realizó y por qué.**
8. Decisiones tomadas, con la alternativa descartada.
9. Riesgos y deuda técnica.
10. Cómo ejecutar y probar.
11. Preguntas abiertas.

**Estados permitidos:** `HECHO` (compila y tiene prueba pasando) · `PARCIAL` (explica qué falta) · `PENDIENTE`. Nunca marques `HECHO` sin evidencia.

---

## 15. Primera instrucción

1. Lee `ESTATUS.md` si existe.
2. Analiza el repositorio del profesor (sección 9). Si no tienes el enlace o no puedes acceder, pídemelo.
3. Hazme las preguntas de la sección 10.
4. Propón el esqueleto de la Fase 0 y **espera mi confirmación**.

No escribas código de dominio hasta que yo confirme.

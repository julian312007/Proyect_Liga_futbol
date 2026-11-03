# ESTATUS — Sistema de Gestión de una Liga de Fútbol

**Equipo:** Santiago Panesso y Julián Valencia · **Stack:** Java 21 + Spring Boot 4.1 + PostgreSQL + Redis
**Última actualización:** 2026-11-02 · **Avance global:** Fases 0 y 1 `HECHO` (compilan y sus pruebas pasan). Fases 2 a 6 `PARCIAL`: integradas en `src/` y compilando con Maven, con pruebas escritas pero **sin ejecutar con Maven ni contra PostgreSQL** (ver el aviso de las Fases 2 a 6). Fase 7 pendiente. El login real con una cuenta de Google sigue sin probarse.

**Estados:** `HECHO` (compila y tiene prueba pasando) · `PARCIAL` (se indica qué falta) · `PENDIENTE`

> **Verificación de la Fase 0 (2026-11-02, máquina de Julián, Windows 10):** `mvnw verify` con JDK 21.0.12 y Maven 3.9.11 (wrapper)
> terminó en `BUILD SUCCESS`: 8 pruebas unitarias (`GlobalExceptionHandlerTest`) y 2 de integración (`InfraestructuraIT`, con
> Testcontainers) sin fallos ni errores. Además, `mvnw spring-boot:run` arrancó la aplicación con el `.env` contra el
> `docker compose` (PostgreSQL 17.11): Flyway aplicó `V1`, Tomcat quedó en el 8080, `/v3/api-docs` y Swagger UI respondieron 200 y una ruta
> protegida respondió 403. Los dos primeros intentos de `verify` fallaron por tiempo de arranque del contenedor de PostgreSQL (ver sección 9).
>
> **Fases 2 a 6 (integradas el 2026-11-02):** las redactaron cinco agentes en paralelo contra un contrato común (`_borrador_CONTRATO_fases_2_a_6.md`),
> se copiaron a `src/` en orden (`_integrar_borradores.ps1`) y `mvnw test-compile` terminó en `BUILD SUCCESS` (166 archivos de código, 80 de pruebas,
> migraciones `V1` a `V11`). **Por indicación de Julián no se ejecutaron pruebas con Maven.** Antes de esa indicación, cada agente ejecutó sus pruebas
> unitarias y de controlador con un ejecutor JUnit propio (sin Maven) y reportó: Fase 2 260/260, Fase 3 147/147, Fase 4 71/71 más 3/3 de consultas,
> Fase 5 135/135, Fase 6 49/50 (la que falló se corrigió y no se reejecutó). Son cifras **reportadas por los agentes, no reproducidas por mí**.
> **Nunca se ejecutaron:** los `*IT` (`CatalogosIT`, `EquiposJugadoresIT`, `PartidosIT`, `EventosPartidoIT`, `EstadisticasIT`), las migraciones `V3` a `V11` con
> `ddl-auto=validate`, ni las consultas JPQL y derivadas contra PostgreSQL. Si alguna consulta es inválida, la aplicación no arranca y fallan todos los `*IT`. Por la regla de
> `HECHO` (compila **y** prueba pasando con Maven), estas fases siguen en `PARCIAL` hasta correr `mvnw test` y `mvnw verify`.
>
> **Verificación de la Fase 1 (2026-11-02, misma máquina, JDK 21.0.12):** el código compila (40 archivos principales y 17 de prueba).
> `mvnw test`: **162 pruebas unitarias, 0 fallos** (`SecurityConfigTest` 30, `NimbusGoogleTokenVerifierTest` 33, `JwtServiceTest` 25,
> `AuthServiceImplTest` 20, `AuthControllerTest` 16, `AdminInicializadorTest` 10, `JwtAuthenticationFilterTest` 10,
> `GlobalExceptionHandlerTest` 8, `DatosDeAutenticacionTest` 8, `RestSecurityHandlersTest` 2). Integración: `InfraestructuraIT` 2/2 en una
> corrida completa de `mvnw verify` y `AuthFlujoIT` 15/15 en una corrida aparte (`-Dit.test=AuthFlujoIT`), porque en la corrida completa
> `AuthFlujoIT` falló los 15 casos por el mismo motivo de entorno (PostgreSQL de Testcontainers no estuvo listo en 3 min con la máquina saturada). **Falta registrar una corrida
> completa de `mvnw verify` en una sola pasada con todo en verde**; se hará al integrar las Fases 2 a 6.

---

## 1. Resumen y avance por fase

| Fase | Contenido | Estado |
|---|---|---|
| 0. Preparación | Análisis del repo del profesor, esqueleto, dependencias, PostgreSQL + Redis, errores | **HECHO** — `mvnw verify` en verde (8 unitarias + 2 IT) y arranque real con PostgreSQL; la conexión a Redis se comprobó en `InfraestructuraIT` (Testcontainers), no desde la app contra el compose (la conexión es perezosa y nada la usa hasta la Fase 6) |
| 1. Autenticación y seguridad | Usuario, **inicio de sesión con Google**, JWT, roles, SecurityConfig, AuthController | **HECHO** (2026-11-02) — con inicio de sesión con Google (sección 8). 162 pruebas unitarias y 17 de integración pasando; ver la verificación abajo. **No probado:** el ingreso con una cuenta real de Google |
| 2. Catálogos | Estadio (controlador independiente), Arbitro, Entrenador, Liga | **PARCIAL** — integrada en `src/`, compila (`mvnw test-compile` OK); 260 pruebas unitarias/de controlador pasaron en el ejecutor del agente autor; falta ejecutarlas con Maven, `CatalogosIT` y V3 a V6 contra PostgreSQL |
| 3. Equipo y Jugador | Relaciones, RN-02, RN-03 | **PARCIAL** — integrada, compila; 147 pruebas pasaron en el ejecutor del agente; falta Maven, `EquiposJugadoresIT` y V7/V8 contra PostgreSQL |
| 4. Partido y calendario | RN-04 a RN-07 | **PARCIAL** — integrada, compila; 71 pruebas + 3 de consultas pasaron en el ejecutor del agente; **sin ejecutar** `PartidoControllerTest`, `PartidosIT` y V9 |
| 5. Goles, tarjetas y cierre | RN-08 a RN-12 | **PARCIAL** — integrada, compila; 135 pruebas pasaron en el ejecutor del agente; falta Maven, `EventosPartidoIT` y V10/V11 |
| 6. Consultas derivadas y caché | Tabla de posiciones, goleadores, sanciones, Redis | **PARCIAL** — integrada, compila; 49 de 50 pruebas pasaron (la que falló se corrigió y **no** se reejecutó); sin ejecutar las de caché, controlador, `EstadisticasIT` ni las consultas JPQL |
| 7. Cierre y entrega | Pruebas de punta a punta, README, `clean`, entrega | PENDIENTE |

## 2. Requisitos del docente

| ID | Requisito | Estado | Evidencia |
|---|---|---|---|
| D-1 | Controlador de autenticación: registro y login (opcional: perfil) | **HECHO** (con Google) | `AuthController`: `POST /api/v1/auth/google` (primer ingreso = registro con rol `USER`; los siguientes = login), `GET/PUT /api/v1/auth/me`. Pruebas: `AuthControllerTest` 16, `AuthServiceImplTest` 20, `AuthFlujoIT` 15. **Pendiente:** confirmar con el docente que Google cubre «registro» y «login» (sección 11) y probar con una cuenta real |
| D-2 | Seguridad: tokens y roles; reglas en `SecurityConfig` o `@PreAuthorize` | **HECHO** | JWT propio HS256 (`JwtService`, `JwtAuthenticationFilter`), roles `ADMIN`/`USER`, `SecurityConfig` real con 401/403 en formato `ApiError`. Matriz 401/403/2xx en `SecurityConfigTest` (30 casos), `JwtServiceTest` 25, `JwtAuthenticationFilterTest` 10, `NimbusGoogleTokenVerifierTest` 33 (firma, emisor, audiencia y vigencia del ID token). Falta aplicar `@PreAuthorize` en los controladores de negocio (Fases 2 a 6) |
| D-3 | Al menos una caché funcional (Redis) | PENDIENTE | Dependencias y `docker-compose` listos; sin `@Cacheable` todavía |
| D-4 | Segundo controlador sobre una entidad independiente, con validaciones y protección por rol | PENDIENTE | — |
| D-5 | Mismo alcance que el repo del profesor, con mejor estructura | PARCIAL | Repo analizado y matriz entregada (Anexo A); la estructura propuesta compila y arranca (`mvnw verify` en verde, 2026-11-02). **Decisión del equipo: es un ejemplo de estilo, no un alcance a replicar.** El resto (entidades, DTO, controladores) llega en las Fases 1 a 6 |
| D-6 | Entrega: enlace al repositorio o ZIP tras `clean` | PENDIENTE | `.gitignore` excluye `target/`, `.env` e IDEs |

## 3. Matriz entidad × capa

| Entidad | Entity | Repository | DTO | Mapper | Service | Controller | Pruebas |
|---|---|---|---|---|---|---|---|
| Liga | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Estadio | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Entrenador | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Arbitro | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Equipo | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Jugador | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Partido | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Gol | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |
| Tarjeta | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL | PARCIAL |

> En esta matriz `PARCIAL` significa: integrado en `src/` y compilando con Maven, con pruebas escritas que el agente autor ejecutó en un ejecutor JUnit propio, pero **sin ejecutar con Maven ni contra PostgreSQL** (ver el aviso de las Fases 2 a 6 al inicio).
| Usuario (ampliación) | HECHO | HECHO | HECHO | HECHO | HECHO | HECHO | HECHO |

## 4. Reglas de negocio

| Código | Regla | Estado | Ubicación en el código | Prueba |
|---|---|---|---|---|
| RN-01 | Victoria 3, empate 1, derrota 0 | PARCIAL | `TablaPosicionesServiceImpl` | `TablaPosicionesServiceImplTest` (casos a mano, desempates) |
| RN-02 | Un jugador pertenece a un solo equipo | PARCIAL | `JugadorServiceImpl.actualizar` (mueve la misma fila) | `JugadorServiceImplTest` `rn02_*`; `EquiposJugadoresIT` (sin ejecutar) |
| RN-03 | Un equipo tiene un entrenador y viceversa | PARCIAL | `EquipoServiceImpl` (crear y actualizar) + `UNIQUE(id_entrenador)` en V7 | `EquipoServiceImplTest` `rn03_*` |
| RN-04 | Un equipo no se enfrenta a sí mismo | PARCIAL | `PartidoServiceImpl.validar` + CHECK en V9 | `PartidoServiceImplTest` |
| RN-05 | Un equipo juega un solo partido por jornada | PARCIAL | `PartidoServiceImpl` + `PartidoRepository.contarPartidosDelEquipoEnJornada` | `PartidoServiceImplTest` |
| RN-06 | Estadio sin dos partidos a la misma fecha y hora | PARCIAL | `PartidoServiceImpl.chocaEnHorario` (`VENTANA_SOLAPE = 0`) + índice único en V9 | `PartidoServiceImplTest` (tabla de ventana); `PartidosIT` (sin ejecutar) |
| RN-07 | Árbitro sin dos partidos a la misma fecha y hora | PARCIAL | igual que RN-06 | igual que RN-06 |
| RN-08 | Gol solo de jugadores de los dos equipos del partido | PARCIAL | `EventoPartidoValidator` | `GolServiceImplTest`, `TarjetaServiceImplTest` (`rn08_*`) |
| RN-09 | Dos amarillas en un partido generan roja | PARCIAL | `TarjetaServiceImpl.registrar` (misma transacción) | `TarjetaServiceImplTest` `rn09_*` |
| RN-10 | El expulsado no juega el siguiente partido de su equipo | PARCIAL | `SuspensionServiceImpl` + `EventoPartidoValidator`; `SancionServiceImpl` lista suspendidos | `SuspensionServiceImplTest`, `GolServiceImplTest`, `TarjetaServiceImplTest` (`rn10_*`) |
| RN-11 | El marcador coincide con los goles registrados | PARCIAL | `CierrePartidoServiceImpl` (`golesDe`) | `CierrePartidoServiceImplTest` (7 marcadores a mano, con autogoles) |
| RN-12 | Un partido finalizado no se modifica ni elimina | PARCIAL | `PartidoServiceImpl` (partido), `EventoPartidoValidator` (goles y tarjetas), `CierrePartidoServiceImpl` | tests `rn12_*` en los tres servicios |
| EST-01 | Estadio: nombre normalizado y único por ciudad | PARCIAL | `EstadioServiceImpl` + índice `ux_estadio_ciudad_nombre` (V3) | `EstadioServiceImplTest` `est01_*`, `EstadioControllerTest`; `CatalogosIT` (sin ejecutar) |
| EST-02 | Estadio: no se elimina si tiene equipos o partidos | PARCIAL | `EstadioServiceImpl.eliminar` + `EstadioConEquiposVerificador` (Fase 3) y `EstadioConPartidosVerificador` (Fase 4) | `EstadioServiceImplTest` `est02_*` y los tests de ambos verificadores |
| EST-03 | Estadio: capacidad > 0 y ciudad obligatoria | PARCIAL | `EstadioRequest` (Bean Validation) + CHECK en V3 | `RequestsCatalogoValidacionTest`, `EstadioControllerTest` |

## 5. Funcionalidades

| # | Funcionalidad | Estado | Endpoint |
|---|---|---|---|
| F1 | CRUD de ligas, equipos, jugadores, entrenadores, estadios y árbitros | PARCIAL | `/api/v1/ligas`, `/equipos`, `/jugadores`, `/entrenadores`, `/estadios`, `/arbitros` (GET paginado, GET por id, POST, PUT, DELETE) |
| F2 | Programar jornadas y partidos | PARCIAL | `POST/PUT/DELETE /api/v1/partidos`, `POST /api/v1/partidos/{id}/iniciar` |
| F3 | Registrar resultado, goles y tarjetas | PARCIAL | `/api/v1/partidos/{id}/goles`, `/tarjetas`, `POST /api/v1/partidos/{id}/finalizar` |
| F4 | Tabla de posiciones (en caché) | PARCIAL | `GET /api/v1/ligas/{id}/tabla-posiciones` (`@Cacheable`; Redis real sin probar) |
| F5 | Tabla de goleadores (en caché) | PARCIAL | `GET /api/v1/ligas/{id}/goleadores` (`@Cacheable`; Redis real sin probar) |
| F6 | Plantel, cuerpo técnico e historial de un equipo | PARCIAL | `GET /api/v1/equipos/{id}/plantel`, `/cuerpo-tecnico`, `/partidos` |
| F7 | Sanciones (con tarjetas y suspendidos) | PARCIAL | `GET /api/v1/ligas/{id}/sanciones`, `/sanciones/suspendidos?jornada=` |
| F8 | Calendario por jornada, equipo o estadio | PARCIAL | `GET /api/v1/partidos?ligaId=&jornada=&equipoId=&estadioId=` |
| A1 | Registro de usuarios (automático en el primer ingreso con Google) | HECHO | `POST /api/v1/auth/google` |
| A2 | Inicio de sesión con Google (ID token de Google → JWT propio) | HECHO | `POST /api/v1/auth/google` (con el verificador de Google simulado en las pruebas; sin cuenta real) |
| A3 | Ver y editar perfil (opcional) | HECHO | `GET` y `PUT /api/v1/auth/me` (solo el nombre; el correo lo da Google) |
| A4 | Cambiar rol de un usuario (opcional) | PENDIENTE | — |

## 6. Qué se realizó

**Análisis y planificación**
- Lectura de la propuesta: 9 entidades, 8 funcionalidades y 12 reglas de negocio.
- Requisitos del docente incorporados al plan; plan en 8 fases y prompt de trabajo (`PROMPT_Liga_Futbol.md`).
- Análisis del repositorio del profesor y matriz de comparación (Anexo A).

**Fase 0 — archivos escritos (compilados y verificados el 2026-11-02, ver aviso al inicio)**
- `pom.xml`: Java 21, Spring Boot 4.1.1, todas las dependencias confirmadas; Lombok + MapStruct con procesadores configurados; Failsafe para pruebas `*IT`.
- Wrapper de Maven (`mvnw`, `mvnw.cmd`, Maven 3.9.11): scripts idénticos byte a byte a los estándar 3.3.4.
- `docker-compose.yml` (PostgreSQL 17 y Redis 7), `.env.example` ficticio, `.gitignore`, `.gitattributes`.
- `application.yaml` solo con variables de entorno; `ddl-auto=validate`; `open-in-view=false`; grupos de Swagger por módulo.
- Migración Flyway `V1__linea_base.sql` (sin tablas).
- Paquetes por capa con `package-info.java` que documentan qué debe y qué no debe hacer cada capa.
- Manejo de errores: `ApiError`, `LigaException` y tres derivadas (404, 409, 422), `GlobalExceptionHandler`.
- `SecurityConfig` **provisional** y `OpenApiConfig` (esquema Bearer).
- Pruebas escritas: `GlobalExceptionHandlerTest` (8 casos, sin Docker) e `InfraestructuraIT` (PostgreSQL, Flyway y Redis con Testcontainers).
- `.mvn/wrapper/maven-wrapper.properties` creado con el contenido de la sección 10; el wrapper descarga Maven 3.9.11 y ejecuta el build.
- `TestcontainersConfig`: tiempo de arranque de los contenedores subido a 3 minutos (el valor por defecto, 60 s, no alcanzó en esta máquina; ver sección 9).

**Fase 1 — autenticación con Google y seguridad (2026-11-02, verificada: ver aviso al inicio)**
- Diseño: el cliente obtiene un ID token con Google Sign-In; `POST /api/v1/auth/google` lo verifica (Nimbus: firma RS256 con las claves públicas de Google, `exp` obligatorio, emisor, audiencia = `GOOGLE_CLIENT_ID` y `email_verified`) y emite el JWT propio (HS256, `sub` = correo, claim `rol`). Sin contraseñas ni *client secret*.
- `Usuario` (`google_sub`, `email`, `nombre`, `picture_url`, `rol`, `activo`, fechas), migración `V2__usuario.sql`, `UsuarioRepository`, `UsuarioMapper`, DTO como `record`, `AuthService`/`AuthServiceImpl`, `AuthController` (`/google`, `/me`).
- `SecurityConfig` real (sin sesión; CSRF deshabilitado con comentario del porqué; público solo `POST /api/v1/auth/google`, Swagger y `/error`), `JwtService`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`, `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` (401/403 con `ApiError`), `AdminInicializador` (crea el ADMIN inicial solo con `LIGA_ADMIN_EMAIL` y lo enlaza en su primer ingreso), `GoogleAuthConfig`.
- Dependencia nueva en el `pom.xml`: `spring-security-oauth2-jose` (versión gestionada por Boot).
- Variables nuevas: `JWT_SECRET` (obligatoria, ≥ 32 caracteres), `GOOGLE_CLIENT_ID` (obligatoria) y `LIGA_ADMIN_EMAIL` (opcional). El `.env` local de Julián ya tiene `JWT_SECRET` generado y `GOOGLE_CLIENT_ID`; falta `LIGA_ADMIN_EMAIL` (decidir qué Gmail será el administrador).
- Redacción inicial a cargo de un agente en `_borrador_fase1/` (carpeta de trabajo; se puede borrar, ya está en `src/`), integrada y verificada aquí. Los originales reemplazados (`SecurityConfig`, `application.yaml`, `application-test.yaml`, `package-info.java`, `.env.example`) se respaldaron fuera del proyecto.

**Verificaciones de la Fase 0 (2026-11-02)**
- `.env` revisado: 8 variables de la Fase 0 presentes (`DB_*`, `REDIS_*`, `SERVER_PORT`), sin comillas, sin espacios sobrantes y sin valores vacíos; ignorado por `.gitignore`. No define `JWT_SECRET` ni `LIGA_ADMIN_*` (se necesitan desde la Fase 1).
- `docker compose up -d`: `liga-postgres` (PostgreSQL 17) y `liga-redis` (Redis 7) en estado `healthy`, publicados solo en 127.0.0.1.
- `mvnw verify`: `BUILD SUCCESS`. Unitarias: 8/8 (`GlobalExceptionHandlerTest`). Integración: 2/2 (`InfraestructuraIT.conectaAPostgresYFlywayAplicoLaLineaBase` y `conectaARedis`).
- `mvnw spring-boot:run` con el `.env`: conexión a `localhost:5432/liga`, Flyway aplicó 1 migración (queda en `v1`), `Started LigaFutbolApplication`, `/v3/api-docs` 200, `/swagger-ui/index.html` 200, `/api/v1/cualquiera` 403 (esperado con la `SecurityConfig` provisional). La aplicación se apagó al terminar.

**Verificaciones previas (antes de poder ejecutar Maven)**
- `pom.xml` bien formado (21 dependencias); los tres YAML se leen correctamente.
- `docker compose config` válido y falla con mensaje claro si falta `DB_PASSWORD`.
- Revisión de sintaxis Java con `javac`: cero errores de sintaxis; los 266 errores restantes son imports de librerías no descargables.
- Las cinco clases de excepción que solo usan el JDK compilan y se ejecutaron en una prueba manual (códigos y formato del mensaje correctos).
- Búsqueda de secretos: ninguno; no hay rastro de los datos del repo del profesor.

## 7. Qué NO se realizó y por qué

- **Comprobar desde la aplicación en marcha que Redis responde:** la conexión a Redis es perezosa y nada la usa todavía; solo `InfraestructuraIT` la comprobó (con Testcontainers). Se cubrirá con la caché de la Fase 6.
- **Compilar y probar con JDK 25:** el `java` por defecto de la máquina de Julián es el 25; todo se ejecutó con JDK 21 (`JAVA_HOME`). El proyecto fija Java 21.
- **Probar en la máquina de Santiago** (otro equipo, otro Docker): sin evidencia.
- **Ingreso con una cuenta real de Google:** las pruebas simulan el verificador y firman ID tokens con una clave RSA propia; nadie ha ingresado todavía con un Gmail real. Para probarlo hacen falta el ID de cliente en `.env`, `LIGA_ADMIN_EMAIL` y una página con el botón de Google (pregunta abierta 10).
- **Una corrida completa de `mvnw verify` con todo en verde en una sola pasada** (ver el aviso de la Fase 1); la variable de entorno `LIGA_ADMIN_EMAIL` tampoco está definida en el `.env`.
- **Cierre de sesión en el servidor, revocación de tokens, *refresh token*, límite de intentos y CORS:** fuera de alcance (el cliente descarta el token).
- **Ejecutar las pruebas de las Fases 2 a 6 con Maven** (`mvnw test` y `mvnw verify`): no se hizo por indicación de Julián. Es lo que falta para pasarlas de `PARCIAL` a `HECHO`. Guía y comandos: `CONTINUAR_MANUALMENTE.md` (la integración a `src/` ya está hecha, no hace falta repetir `_integrar_borradores.ps1`).
- **Validar contra PostgreSQL** las migraciones `V3` a `V11` con `ddl-auto=validate`, los `*IT` de las Fases 2 a 6 y todas las consultas JPQL/derivadas de los repositorios (`PartidoEventosRepository`, `Estadisticas*Repository`, `GolRepository`, `TarjetaRepository`, `PartidoRepository`...).
- **Probar la caché con Redis real** (solo se probó con una caché en memoria y con una conexión Redis simulada que falla).
- **Fase 7** (escenario de punta a punta, revisión de seguridad y rendimiento, README, `clean`, entrega): al final, como se acordó.
- Las carpetas `_borrador_*`, `_integrar_borradores.ps1` y `CONTINUAR_MANUALMENTE.md` son material de trabajo ya integrado: se pueden borrar antes de entregar (D-6), pero no se borraron. Cada fase se integrará en orden, con compilación y pruebas, y recién entonces pasará a `HECHO`.
- **Datos de ejemplo (Anexo B):** no incluidos en esta tanda de borradores.
- **Caché:** falta `RedisCacheConfig`, `@EnableCaching` y el `CacheErrorHandler` (Fase 6).
- **README:** se redacta en la Fase 7, con contexto completo (regla 1 del prompt).

## 8. Decisiones tomadas

| Decisión | Alternativa descartada |
|---|---|
| Java 21 | Java 25 (también LTS, pero sin necesidad real) |
| Spring Boot 4.1.x (línea del profesor) | Boot 3.5: sin soporte OSS desde junio de 2026 |
| PostgreSQL y Redis locales con Docker | PostgreSQL en Supabase |
| JWT con `jjwt` + `jjwt-gson` | `oauth2-resource-server`; `jjwt-jackson` (depende de Jackson 2) |
| Rutas `/api/v1/...` y columnas `id_<entidad>` | `/api/...` sin versión y `id` simple |
| Todas las dependencias de las secciones 4.2 y 4.3 del prompt | Solo las necesarias |
| Un grupo de Swagger por módulo, declarado en `application.yaml` | `GroupedOpenApi` en Java (menos riesgo de API sin compilar); documentación en archivos aparte |
| Repo del profesor como ejemplo de estilo | Replicar su alcance |
| Datos de ejemplo: 20 clubes y estadios reales de la Liga BetPlay 2026; entrenadores, jugadores, árbitros, partidos y resultados ficticios generados con script | Todo real (plantillas que cambian cada mercado, riesgo de errores); solo clubes sin personas |
| Failsafe: pruebas `*IT` (con Docker) en `verify`, `*Test` en `test` | Todo en `test`: obligaría a tener Docker siempre |
| Tiempo de arranque de contenedores de prueba: 3 min (`TIEMPO_ARRANQUE` en `TestcontainersConfig`) | Dejar los 60 s por defecto: fallaron dos corridas seguidas en un equipo con 6 GB de RAM. Contra: si Docker está realmente roto, el fallo tarda hasta 3 min en aparecer |
| Compilar y probar con JDK 21 fijando `JAVA_HOME` (el PATH apunta al 25) | Usar el JDK 25 por defecto: no probado |
| JPA, Redis/Cache, Validation, MapStruct, Flyway y springdoc 3.x añadidos a la lista inicial | Mantener la lista inicial (no permitía persistir ni cachear) |
| Empezar desde cero, entregando primero prompt y plan | Implementar el código de inmediato |
| JWT sin sesión con entidad `Usuario` (requisito del docente) | HTTP Basic con usuario en memoria |
| **Inicio de sesión con Google (indicado por el equipo el 2026-11-02); «solo Google, sin contraseña de respaldo» confirmado el mismo día.** Mecanismo implementado en la Fase 1 (propuesto por el asistente; sin objeciones del equipo hasta ahora): el cliente obtiene un ID token de Google; `POST /api/v1/auth/google` lo verifica con Nimbus (`spring-security-oauth2-jose`: firma, emisor, audiencia = `GOOGLE_CLIENT_ID`, `email_verified`) y emite el JWT propio con rol `ADMIN`/`USER`. El primer ingreso crea el `Usuario` (`USER`); el administrador inicial se crea por `LIGA_ADMIN_EMAIL` y se enlaza en su primer ingreso. Sin contraseñas ni *client secret* | (a) `oauth2Login` con redirección en el servidor: exige sesión/redirecciones, choca con la API `STATELESS` y es difícil de probar. (b) Aceptar el ID token de Google directamente como Bearer (`oauth2-resource-server`): sin JWT propio, depende de la vigencia de Google (1 h) y de una consulta a la BD por petición para el rol. (c) Usuario y contraseña propios: descartado por el equipo. **Contra del diseño elegido:** necesita un cliente que obtenga el ID token (una página con el botón de Google, o OAuth Playground para pruebas) y un ID de cliente de Google Cloud; Swagger por sí solo no puede iniciar el flujo de Google |
| Caché con anotaciones + Redis | `RedisTemplate` manual |

## 9. Riesgos y deuda técnica

**Resueltos con la verificación del 2026-11-02:**
- Los artefactos de Boot 4 (`spring-boot-starter-flyway`, `testcontainers-postgresql`, `springdoc-openapi-starter-webmvc-ui` 3.2.0) se resuelven y funcionan: el proyecto compila, Swagger UI y `/v3/api-docs` responden 200.
- Los paquetes de Testcontainers 2.x y `@ServiceConnection` compilan y las pruebas de integración pasan.
- El `405` pasa por el `@RestControllerAdvice` (`GlobalExceptionHandlerTest` 8/8).
- El primer arranque con **cero entidades** y `ddl-auto=validate` funciona con Hibernate 7; `V1__linea_base.sql` (`SELECT 1;`) la acepta Flyway.

**Entorno de desarrollo (observado el 2026-11-02):**
- **Equipo justo de memoria:** 5,9 GB de RAM (≈0,5 GB libres durante las pruebas). Dos corridas seguidas de `verify` fallaron porque el contenedor de PostgreSQL de Testcontainers no estuvo listo en 60 s (`initdb` avanzaba, pero lento; Ryuk tardó más de 80 s en arrancar). La tercera pasó con el timeout de 3 min, aunque PostgreSQL arrancó en 55 s, es decir, también habría cabido en el límite anterior: **no se puede afirmar que el cambio de timeout fuera lo que la arregló**, y la prueba puede volver a ser lenta o inestable en equipos así. Mitigación: cerrar programas pesados antes de `verify`.
- **Docker Desktop atascado por WSL:** al abrirlo, el motor no arrancó (`wsl.exe -l -v` no respondía, error `getting list of WSL2 integrated distros: context deadline exceeded`). Se resolvió cerrando Docker Desktop, ejecutando `wsl --shutdown` y abriéndolo de nuevo. Si se repite, hacer lo mismo.
- **JDK por defecto = 25:** el `java` del PATH de Julián es el 25; el proyecto exige 21. Sin `JAVA_HOME` apuntando al 21, `mvnw` usaría el 25 (no probado). Ver sección 10.
- Los *build* con Maven son lentos en este equipo (la prueba de integración tarda ~1,5 min); es normal.

**Fases 2 a 6 (integradas sin ejecutar pruebas con Maven) — de mayor a menor probabilidad de romper:**
- Consultas JPQL y derivadas sin validar contra Hibernate/PostgreSQL (`@Lock PESSIMISTIC_WRITE`, `join fetch`, `@EntityGraph`, Specifications): una inválida impide arrancar la aplicación y rompe todos los `*IT`.
- Migraciones `V3` a `V11` frente a las entidades con `ddl-auto=validate` (longitudes de `VARCHAR`, tipos, enums): solo compilan.
- `SancionServiceImpl` (Fase 6) exige el bean `SuspensionService` de la Fase 5: con todas las fases juntas existe, pero separarlas rompe el arranque.
- `@EnableCaching(order = ...)` en `RedisCacheConfig` pone la caché por fuera de la transacción para que el `@CacheEvict` ocurra tras el commit; no comprobado con Redis real. Un nombre de caché nuevo distinto de los de `NombresCache` falla en Redis y no en las pruebas (modo simple).
- `Pageable` y `@Min` en parámetros dentro de `@WebMvcTest` (Boot 4): los controladores de las Fases 2 y 3 lo ejecutaron con éxito; los de la 4 y la 6 no se ejecutaron.
- Colisión de nombres de bean al integrar: no hubo (compila y no hay clases duplicadas); queda por comprobar el arranque.
- Reglas que el plan no pide y los borradores **no** implementan (propuestas): que los equipos de un partido pertenezcan a su liga; que cambiar de liga a un equipo con partidos dé 409; unicidad de equipo y de liga sin distinguir mayúsculas respaldada en BD; `@Version` en `Partido` contra actualizaciones simultáneas; borrar la roja automática al borrar la 2.ª amarilla.
- Zona horaria: "hoy" y "año actual" de las validaciones de `Equipo` y `Jugador` usan la zona del servidor (propuesta: un `Clock` con `America/Bogota`).

**Seguridad:**
- El `application.yaml` del repo del profesor expone usuario y contraseña reales de una base en Neon. No se usaron; conviene avisarle.
- `SecurityConfig` provisional: sin filtro JWT, los endpoints protegidos responden `403` y no `401` hasta la Fase 1.
- JWT: secreto débil o expuesto, fuerza bruta en el login (sin *rate limiting*) y HTTPS obligatorio fuera de desarrollo.
- Redis del `docker-compose` sin contraseña: aceptable solo porque publica en `127.0.0.1`.
- Login con Google (implementado en la Fase 1): el ADMIN inicial se crea solo con su correo y queda enlazado a la primera cuenta de Google que ingrese con ese correo **verificado**; si el correo se reasignara o el dominio cambiara de dueño, otra persona podría reclamarlo antes. Mitigaciones: solo se enlaza una fila sin `google_sub`, se exige `email_verified` y nunca se reasigna un `sub`; recomendación: usar un Gmail propio del equipo y hacer su primer ingreso apenas se despliegue.
- Login con Google: el ID token no usa `nonce` (quien lo robe puede ingresar durante su vigencia, ~1 h; se mitiga con HTTPS); el primer ingreso necesita red para descargar las claves de Google (se cachean unos 5 min); cada solicitud autenticada hace una consulta a la base de datos (por el índice único del correo); dos primeros ingresos simultáneos de una misma cuenta pueden chocar con el índice único (uno recibe 409 y reintenta); si el correo de Google cambia, los JWT anteriores dejan de servir (el `sub` propio es el correo); sin CORS configurado si habrá un frontend en otro origen.

**Diseño:**
- RN-10 no puede impedir que el jugador "juegue" porque el modelo no tiene alineaciones; se aplica rechazando sus eventos y listándolo como suspendido.
- RN-06 y RN-07 literales (misma fecha y hora exactas) permiten solapes reales de partidos; se recomienda una ventana de ~2 h. **Decisión del equipo pendiente.**
- Redis es dependencia de ejecución: se mitiga con `docker-compose` y un `CacheErrorHandler` (Fase 6).
- `mvnw` pierde el permiso de ejecución al comprimir o commitear desde Windows; en Linux o macOS puede ser necesario `chmod +x mvnw` o ejecutar `sh mvnw`.

## 10. Cómo ejecutar y probar (con IntelliJ IDEA)

> Los pasos de **línea de comandos** (`.env`, `docker compose up -d`, `mvnw verify`, `mvnw spring-boot:run`) se ejecutaron el 2026-11-02
> en la máquina de Julián. Los pasos **de IntelliJ** (importar, anotaciones, ejecutar `main`) **no se han ejecutado todavía**.
> La primera importación descarga las dependencias desde Maven Central, así que necesita internet.

Requisitos: JDK 21 (IntelliJ puede descargarlo: File > Project Structure > SDKs > Add SDK > Download JDK > versión 21),
Docker Desktop abierto y el plugin de Lombok (viene incluido en las versiones recientes de IntelliJ).

**Abrir el proyecto**
1. File > Open, elegir la carpeta `D:\Proyect_Liga_futbol` (la que contiene `pom.xml`) y aceptar "Trust Project".
2. Esperar a que termine la importación de Maven (barra de progreso inferior). Si no arranca sola: clic derecho sobre
   `pom.xml` > Add as Maven Project; luego, en el panel Maven, el botón "Reload All Maven Projects".
3. File > Project Structure > Project: SDK = 21 y Language level = 21.
4. File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors: marcar
   "Enable annotation processing" (necesario para Lombok y MapStruct).

**Configurar y levantar PostgreSQL y Redis**
5. Duplicar `.env.example` como `.env` (clic derecho > Copy, luego Paste con el nombre `.env`) y editar `DB_USER` y
   `DB_PASSWORD` con valores propios. El `.env` no se sube al repositorio.
6. Abrir la terminal de IntelliJ (View > Tool Windows > Terminal) y ejecutar `docker compose up -d`.

**Probar y ejecutar**
7. Panel Maven > liga > Lifecycle > doble clic en `test` (pruebas unitarias, no necesitan Docker). Después `verify`
   (suma las pruebas de integración, que sí necesitan Docker).
8. Abrir `LigaFutbolApplication` y pulsar el triángulo verde junto a `main` para arrancar la API.
   Si falla con `Could not resolve placeholder 'DB_USER'`, el `.env` no está en la raíz del proyecto o la
   configuración de ejecución no usa la raíz como directorio de trabajo (Run > Edit Configurations > Working directory).

**Desde la línea de comandos (PowerShell, probado el 2026-11-02)**

El `java` del PATH puede no ser el 21 (en la máquina de Julián es el 25). Fija el JDK 21 solo para esa terminal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
docker compose up -d
.\mvnw.cmd verify
```

Con poca RAM, cierra programas pesados antes de `verify`. Si Docker Desktop no arranca por WSL: ciérralo, ejecuta `wsl --shutdown` y ábrelo de nuevo.

**Wrapper de Maven (`mvnw`)**

El archivo `.mvn/wrapper/maven-wrapper.properties` **ya existe** (creado el 2026-11-02) y el wrapper funciona. IntelliJ trae su propio
Maven, así que el wrapper solo importa para que el docente pueda ejecutar `mvnw clean` sin tener Maven instalado (D-6). Si el
archivo se perdiera (por ejemplo, al comprimir o copiar el proyecto), se recrea con este contenido exacto:

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
```

Si prefieren no usar el wrapper, pueden borrar `mvnw` y `mvnw.cmd`.

**Otros datos**
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Parar la infraestructura: `docker compose down` (con `-v` borra los datos de PostgreSQL).
- Variables de las Fases posteriores (`JWT_SECRET`, `LIGA_ADMIN_*`) ya están listadas en `.env.example`; la Fase 0 aún no las lee.

## 11. Preguntas abiertas

1. ¿JDK 21 y Docker están instalados en las máquinas del equipo? (Máquina de Julián: sí, JDK 21.0.12 y Docker Desktop 29.9.2. Falta confirmar la de Santiago.)
2. ¿Cuál será la estrategia de repositorio y ramas? (No se hacen commits ni push sin que se pida.)
3. RN-06 y RN-07: ¿fecha y hora **exactas** (literal) o ventana de solape de ~2 h (recomendada)?
4. ¿Roles `ADMIN` y `USER` únicamente, y `GET` con autenticación? (Se asume que sí.)
5. ¿Se confirma `Estadio` como controlador independiente (D-4)? (Se asume que sí.)
6. ¿Entrega por enlace de GitHub o por ZIP?
7. ¿Se avisa al profesor de que su repositorio público expone credenciales de base de datos?
8. Datos de ejemplo: ¿la liga se llamará «Liga BetPlay Dimayor» con temporada «2026»? (Se asume que sí.)
9. ~~Login con Google: ¿solo Google, o también cuenta con contraseña como respaldo?~~ **Resuelta (2026-11-02): solo Google.**
10. Login con Google: ¿habrá frontend (página con el botón de Google) o solo API? Sin frontend, las pruebas manuales usan OAuth 2.0 Playground para obtener el ID token.
11. Login con Google: ¿quién crea el ID de cliente de OAuth en Google Cloud Console? Hace falta antes de probar con una cuenta real (solo el ID de cliente, no el *client secret*), guardado en `.env` como `GOOGLE_CLIENT_ID`.
12. ¿El docente acepta Google como «registro y login» de D-1?
13. Partido en juego: ¿se puede editar o borrar un partido `EN_JUEGO`? La Fase 4 lo rechaza (409 `PARTIDO_EN_JUEGO`); el plan solo prohíbe el `FINALIZADO` (RN-12). Es un supuesto del borrador.
14. ¿Los dos equipos de un partido deben pertenecer a la liga del partido? El plan no lo pide y no se implementó, pero la tabla de posiciones depende de ello.
15. Sanciones (`GET /ligas/{id}/sanciones`): la Fase 6 cuenta tarjetas de partidos `EN_JUEGO` y `FINALIZADO`; ¿solo `FINALIZADO` como la tabla y los goleadores?
16. Goleadores y sanciones muestran el equipo **actual** del jugador (RN-02): si cambia de equipo, sus goles y tarjetas antiguos figuran con el equipo nuevo. ¿Se acepta o se guarda el equipo en cada evento?

---

## Anexo A. Matriz de comparación con el repo del profesor

Repositorio: `https://github.com/fabiancvuniajc/apiUniversitaria` (12 archivos, un commit). Es un ejemplo mínimo: una sola entidad funcional (`Estudiante`), un `CursoController` vacío y ninguna de las piezas que pide el docente en D-1 a D-4.

| Elemento | Repo del profesor | Nuestro diseño | Acción |
|---|---|---|---|
| Spring Boot / Java | Boot 4.1.1, `java.version` 17 | Boot 4.1.1, Java 21 | Igualar la línea Boot; Java 21 por decisión del equipo |
| Dependencias | webmvc, devtools, lombok, data-jpa, postgresql, mapstruct 1.6.3, springdoc **2.5.0** | + security, validation, redis, cache, flyway, jjwt, testcontainers | Igualar y ampliar; springdoc pasa a 3.x (línea de Boot 4) |
| Seguridad / roles | No hay | JWT, `ADMIN`/`USER` | Añadir (D-1, D-2) |
| Caché | No hay | Redis con `@Cacheable` | Añadir (D-3) |
| Entidades | `Estudiante` (JPA) y `Curso` (POJO sin anotaciones) | 9 entidades + `Usuario` | Ampliar |
| `@Data` en entidades | Sí | `@Getter/@Setter` | Mejorar |
| DTO | Clases `@Data` sin validaciones; el `PUT` recibe la entidad | `record` con Bean Validation | Mejorar |
| Controlador | Devuelve entidades, sin `@Valid`; `CursoController` vacío | DTO, `@Valid`, `@PreAuthorize` | Mejorar |
| Servicio | Interfaz + impl. `actualizar` y `borrar` operan sobre una lista en memoria y no tocan la BD; `buscar` devuelve `null` | Interfaz + impl, `@Transactional`, excepciones de dominio | Igualar patrón, corregir lógica |
| Mapper | MapStruct con `INSTANCE` estático, dentro de `services/` | `componentModel = spring`, paquete `mapper/` | Mejorar (se replica la configuración Lombok + MapStruct) |
| Paquetes | Main en `application/`, con `@ComponentScan`, `@EntityScan` y `@EnableJpaRepositories` manuales | Main en el paquete raíz | Mejorar |
| Rutas | `/api/v1/estudiantes`, búsqueda por `documento` | `/api/v1/<recurso>` | Igualar prefijo |
| Tablas | `estudiante`, columna `id_estudiante`, `IDENTITY` | `id_<entidad>` | Igualar convención |
| Swagger | `@Tag`, `@Operation`, `@ApiResponse`; `groups.enabled` sin grupos definidos | Un grupo por módulo | Igualar anotaciones, completar grupos |
| Errores | Sin manejador global | `GlobalExceptionHandler` + `ApiError` | Añadir |
| Pruebas | Dependencia presente, 0 tests | JUnit, Mockito, slice tests, Testcontainers | Añadir |
| Configuración | Credenciales en texto plano; el bloque `jpa:` está indentado dentro de `datasource:`, así que Spring lo ignora | Variables de entorno, Flyway | Mejorar |

No ejecutado y por eso solo riesgo: el servicio del profesor inyecta un `List<Estudiante>` sin ningún bean que lo provea, y springdoc 2.5.0 es de la línea de Boot 3.

---

## Anexo B. Datos de ejemplo de la Liga colombiana

**Decisión del equipo (2026-11-02):** una base de desarrollo con la Liga BetPlay Dimayor 2026. Los 20 clubes y sus estadios son
reales; entrenadores, jugadores y árbitros son ficticios; el calendario, los resultados, los goles y las tarjetas son ficticios y
se generan con un script (todos contra todos, una vuelta: 19 jornadas y 190 partidos).

**Estado: PARCIAL.** Los datos reales de clubes y estadios ya están verificados y guardados en
`datos/liga-colombiana/` (`clubes_estadios.csv` y `README.md` con fuentes y discrepancias entre fuentes). **Nada se ha cargado
en una base**: no existen tablas, migraciones ni el script generador; las tablas llegan por fases.

| Fase | Qué se carga | Estado |
|---|---|---|
| 2. Catálogos | Liga («Liga BetPlay Dimayor», temporada 2026), 17 estadios reales, entrenadores y árbitros ficticios (20 y unos 16) | PENDIENTE |
| 3. Equipo y Jugador | 20 equipos con su estadio local y entrenador; unos 440 jugadores ficticios (22 por equipo: 3 porteros, 7 defensas, 7 medios, 5 delanteros) | PENDIENTE |
| 4. Partido y calendario | 19 jornadas × 10 partidos, con fecha, hora, estadio y árbitro | PENDIENTE |
| 5. Goles, tarjetas y cierre | Resultados, goles y tarjetas de los partidos generados | PENDIENTE |

**Mecanismo previsto:** migraciones repetibles `R__seed_*.sql` en `src/main/resources/db/seed`, idempotentes
(`ON CONFLICT DO NOTHING`), que solo se ejecutan con el perfil `seed`. Así no se mezclan con las migraciones del esquema ni con
las pruebas.

**Reglas que debe cumplir el generador** (la semilla debe ser una prueba más de las reglas de negocio):
- RN-04 y RN-05: nadie juega contra sí mismo ni dos veces en la misma jornada (se usa el método del círculo para el calendario).
- RN-06: los tres estadios compartidos (Atanasio Girardot, El Campín y Metropolitano de Techo) nunca reciben dos partidos a la misma hora.
- RN-07: ningún árbitro dirige dos partidos a la misma hora.
- RN-08, RN-09 y RN-11: goles solo de jugadores de los dos equipos; dos amarillas a un jugador en un partido generan roja; el marcador coincide con los goles.
- RN-10: un expulsado no aparece en eventos del siguiente partido de su equipo.
- RN-12: los partidos generados quedan `FINALIZADO`.
- Reproducible: misma semilla aleatoria, mismos datos; ningún nombre de persona real.

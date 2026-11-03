# Sistema de Gestión de una Liga de Fútbol — guía de presentación

**Autores:** Santiago Panesso y Julián Valencia · Universidad Antonio José Camacho (Cali)
**Qué es:** una API REST para administrar una liga de fútbol: ligas, equipos, jugadores, entrenadores, árbitros,
estadios, partidos, goles y tarjetas, con tabla de posiciones, goleadores y sanciones.
**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL 17 · Redis 7 · Flyway · JWT + Google Sign-In · Swagger UI · Docker Compose

---

## 1. Idea central (30 segundos)

> «Es el backend de una liga: se registran los datos de la competencia, se programan partidos y se registran goles y
> tarjetas; al finalizar un partido la API calcula sola la tabla de posiciones, los goleadores y las sanciones. El acceso
> es con la cuenta de Google y cada acción está protegida por rol.»

## 2. Cómo está construido

Arquitectura por capas; cada capa tiene una sola responsabilidad:

| Capa | Qué hace | Ejemplo |
|---|---|---|
| **Controller** | Recibe la petición HTTP, valida el cuerpo y delega. No tiene lógica. | `PartidoController` |
| **Service** | Aquí viven **todas las reglas de negocio**. Es transaccional. | `PartidoServiceImpl` |
| **Repository** | Acceso a PostgreSQL con Spring Data JPA. | `PartidoRepository` |
| **Entity** | Las 9 entidades del dominio + `Usuario`. | `Partido`, `Gol`, `Tarjeta` |
| **DTO** | Objetos de entrada/salida (`record`) con validaciones; nunca se expone una entidad. | `PartidoRequest` |
| **Mapper** | Convierte entidad ↔ DTO (MapStruct). | `PartidoMapper` |
| **Exception** | Un único manejador de errores con formato uniforme (`ApiError`). | `GlobalExceptionHandler` |

Decisiones de diseño que conviene mencionar:
- **Flyway** versiona el esquema (migraciones `V1` a `V11`) y Hibernate solo **valida** que las entidades coincidan con las tablas.
- **Reglas en dos niveles:** el servicio las valida y devuelve un error claro; la base de datos (índices únicos, `CHECK`, claves foráneas) es la última defensa ante concurrencia.
- **Redis como caché** de la tabla de posiciones y de goleadores; si Redis falla, la API sigue respondiendo desde PostgreSQL.
- **Configuración por variables de entorno** (archivo `.env`, que nunca se sube al repositorio).

## 3. Seguridad

1. **Ingreso con Google** (Google Sign-In): el usuario entra con su cuenta de Google; la API verifica el token de Google
   (firma, emisor, audiencia y correo verificado) y entrega un **JWT propio** (HS256) con el rol. No se guardan contraseñas.
2. **Dos roles:** `ADMIN` (crea, edita, borra y ejecuta acciones) y `USER` (solo consulta).
3. **Sin token → 401. Rol insuficiente → 403.** Los errores salen en el mismo formato JSON.
4. El primer ingreso de una cuenta la registra con rol `USER`. El administrador inicial se define con `LIGA_ADMIN_EMAIL`.

Página de ingreso para la demostración: `http://localhost:8080/login-google.html`.

## 4. Reglas de negocio (lo que hace «inteligente» a la API)

| Código | Regla | Respuesta si se incumple |
|---|---|---|
| RN-01 | Victoria 3 puntos, empate 1, derrota 0 (tabla de posiciones) | — |
| RN-02 | Un jugador pertenece a un solo equipo; cambiarlo lo **mueve**, no lo duplica | 409 si el dorsal ya está ocupado |
| RN-03 | Un equipo tiene un solo entrenador y un entrenador dirige a un solo equipo | 409 |
| RN-04 | Un equipo no juega contra sí mismo | 422 |
| RN-05 | Un equipo juega un solo partido por jornada | 409 |
| RN-06 / RN-07 | Un estadio / un árbitro no pueden tener dos partidos a la misma fecha y hora | 409 |
| RN-08 | Un gol solo lo marca un jugador de alguno de los dos equipos del partido | 422 |
| RN-09 | Dos amarillas a un jugador en un partido generan una **roja automática** | — |
| RN-10 | El expulsado no juega el siguiente partido de su equipo | 422 |
| RN-11 | El marcador final debe coincidir con los goles registrados | 422 |
| RN-12 | Un partido finalizado no se modifica ni se elimina | 409 |
| EST-01 | Estadio: nombre único por ciudad (sin distinguir mayúsculas) | 409 |
| EST-02 | Estadio: no se elimina si tiene equipos o partidos | 409 |
| EST-03 | Estadio: capacidad mayor que cero y ciudad obligatoria | 400 |

Ciclo de vida de un partido: `PROGRAMADO → EN_JUEGO → FINALIZADO`. Goles y tarjetas solo se registran con el partido en juego.

## 5. Funciones (qué endpoints ofrece)

Todas las rutas empiezan por `/api/v1`. En Swagger están agrupadas por módulo.

| Módulo | Rutas | Acceso |
|---|---|---|
| **Autenticación** | `POST /auth/google` (registro e ingreso) · `GET/PUT /auth/me` (perfil) | público / autenticado |
| **Catálogos** | `/ligas`, `/estadios`, `/arbitros`, `/entrenadores`: listar (paginado), consultar, crear, editar, borrar | `GET` USER/ADMIN · resto ADMIN |
| **Equipos y jugadores** | `/equipos`, `/jugadores` (CRUD) · `GET /equipos/{id}/plantel` · `/equipos/{id}/cuerpo-tecnico` · `/equipos/{id}/partidos` | `GET` USER/ADMIN · resto ADMIN |
| **Partidos** | `/partidos` (programar, calendario con filtros por liga, jornada, equipo y estadio) · `POST /partidos/{id}/iniciar` | `GET` USER/ADMIN · resto ADMIN |
| **Goles y tarjetas** | `POST/GET/DELETE /partidos/{id}/goles` y `/tarjetas` · `POST /partidos/{id}/finalizar` | `GET` USER/ADMIN · resto ADMIN |
| **Estadísticas** | `GET /ligas/{id}/tabla-posiciones` · `/goleadores` · `/sanciones` · `/sanciones/suspendidos?jornada=` | USER/ADMIN |

La tabla de posiciones y los goleadores se calculan solo con partidos **finalizados** y se guardan en Redis (10 minutos);
al finalizar un partido la caché de esa liga se invalida sola.

## 6. Cómo ejecutarlo (para mostrarlo en vivo)

Requisitos: JDK 21, Docker Desktop abierto y el archivo `.env` (plantilla: `.env.example`) con la base de datos,
`JWT_SECRET`, `GOOGLE_CLIENT_ID` y `LIGA_ADMIN_EMAIL`.

```powershell
# 1. Base de datos y caché
docker compose up -d

# 2. Aplicación (con JDK 21)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

La primera vez, Flyway crea las tablas solo. La aplicación tarda 1 a 3 minutos en arrancar en un equipo con poca memoria.

| Qué | Dirección |
|---|---|
| Ingreso con Google | http://localhost:8080/login-google.html |
| Swagger UI (probar la API) | http://localhost:8080/swagger-ui.html |

Cómo usar Swagger: entrar con Google → copiar el token → botón **Authorize** → pegar el token (sin escribir «Bearer»).

## 7. Datos de demostración (Liga dimayor)

La liga está cargada por la propia API (con los mismos endpoints `POST` que se muestran en Swagger), así que sirve también como prueba de que funcionan.

| Qué | Cantidad | Origen |
|---|---|---|
| Liga | «Liga dimayor», temporada 2026 | — |
| Equipos | 20 | **Reales**: los clubes de la liga, con su ciudad y año de fundación |
| Jugadores | 440 (22 por equipo: 3 porteros, 7 defensas, 7 mediocampistas, 5 delanteros) | Ficticios |
| Entrenadores / Estadios | 20 / 20 (uno por equipo) | Ficticios |
| Árbitros | 17 (incluye a «Roldan») | Ficticios |
| Partidos | 191: jornadas 1 a 18 jugadas (180), jornada 19 programada (10) y el partido de vuelta Deportivo Cali – América de Cali programado como jornada 20 | Ficticios |
| Goles y tarjetas | 430 goles (normales, penales y autogoles) · 657 tarjetas (611 amarillas y 46 rojas, algunas por doble amarilla) | Ficticios |

Los nombres de personas, estadios y resultados son inventados; solo los nombres de los clubes son reales. Al cargarlos, la tabla
de posiciones quedó con Deportivo Cali de líder (36 puntos) y cuatro goleadores empatados con 6 goles. Si se juegan partidos de la jornada 19 la tabla cambia.

**Para la demostración en vivo** quedan 10 partidos `PROGRAMADO` en la jornada 19 (`GET /partidos?ligaId=2&jornada=19`). Hay dos jugadores
suspendidos para esa jornada por una roja en la 18 (`GET /ligas/2/sanciones/suspendidos?jornada=19`): Rafael Suárez Cortés (Cúcuta Deportivo) y
Óscar Durán Quiroz (Once Caldas). Si se intenta registrar un gol o una tarjeta suyos en su partido de la jornada 19, la API responde 422 (RN-10).

**Cargar de nuevo estos datos** (por ejemplo en otro computador): levantar `docker compose up -d`, arrancar la aplicación una vez (Flyway crea las
tablas) y cargar el volcado en la base vacía. `<DB_USER>` y `<DB_NAME>` son los de tu `.env`:

```powershell
docker cp datos/demo/liga_dimayor_demo.sql liga-postgres:/tmp/demo.sql
docker exec liga-postgres psql -U <DB_USER> -d <DB_NAME> -f /tmp/demo.sql
```

El volcado no incluye usuarios (el primer ingreso con Google crea el tuyo). Para empezar de cero: `docker compose down -v` borra la base de datos.

## 8. Guion sugerido de la demostración (10 a 12 minutos)

1. **Contexto (1 min):** qué es el sistema y el stack (sección 1).
2. **Arquitectura y seguridad (2 min):** capas, Flyway, Redis y el ingreso con Google (secciones 2 y 3).
3. **Ingresar (1 min):** abrir la página de ingreso, entrar con Google, copiar el token y autorizar en Swagger.
4. **Consultar (2 min):** `GET /ligas/{id}/tabla-posiciones`, `/goleadores`, `/sanciones` y el calendario de partidos.
5. **Mostrar las reglas fallando (2 min):** intentar un partido de un equipo contra sí mismo (422), repetir un dorsal (409), borrar un estadio con equipos (409), llamar sin token (401).
6. **Jugar un partido en vivo (3 min):** tomar un partido `PROGRAMADO` → `iniciar` → registrar un par de goles y una amarilla (dos amarillas dan roja) → `finalizar` con un marcador que **no** coincida (422) y luego con el correcto → volver a consultar la tabla: cambió sola.
7. **Cierre (1 min):** qué falta (sección 9) y preguntas.

Tip: antes de la demostración, abrir una vez la tabla de posiciones y los goleadores para comprobar que todo responde.

## 9. Estado honesto del proyecto

- **Funciona de punta a punta:** se cargó la liga completa por la API (catálogos, equipos, jugadores, partidos, goles, tarjetas y cierre de partidos) sobre PostgreSQL y Redis reales, y la tabla, los goleadores y las sanciones responden con los valores esperados.
- **Pruebas automáticas:** la autenticación y la seguridad (Fases 0 y 1) tienen sus pruebas ejecutadas y pasando con Maven. Las fases de dominio (2 a 6) tienen pruebas escritas y compilan, pero **todavía no se han ejecutado con Maven** (`mvnw test` / `mvnw verify`); es lo primero que falta cerrar.
- **Pendiente (Fase 7):** escenario de pruebas de punta a punta, revisión de seguridad y rendimiento, y README final.
- **Limitaciones conocidas:** no hay cierre de sesión en el servidor (el cliente descarta el token), ni límite de intentos, ni CORS para un frontend en otro origen; la página de ingreso es mínima.

## 10. Preguntas que pueden hacer (y cómo responder)

- **¿Por qué ingreso con Google y no usuario y contraseña?** Es un requisito del equipo: evita guardar contraseñas y delega la verificación de identidad en Google; la API solo guarda el correo, el nombre y el rol.
- **¿Por qué un JWT propio si ya hay token de Google?** Para controlar el rol y la vigencia sin depender de Google en cada petición; la API verifica a Google una sola vez, al ingresar.
- **¿Para qué Redis?** La tabla de posiciones y los goleadores son consultas agregadas y muy leídas; se cachean y se invalidan al finalizar un partido. Si Redis cae, la API sigue funcionando.
- **¿Por qué Flyway?** El esquema queda versionado y reproducible; cualquiera levanta la misma base de datos con una sola ejecución.
- **¿Cómo evitan que dos partidos choquen en el mismo estadio?** Doble defensa: el servicio responde un 409 claro y la base de datos tiene un índice único como último respaldo.
- **¿Y si el marcador no coincide con los goles?** Al finalizar, la API recalcula el resultado a partir de los goles (los autogoles suman al rival) y rechaza el cierre con un 422 si no coincide.

## 11. Cómo entregar o exportar el proyecto

Dos opciones (la que pida el docente):

1. **Enlace al repositorio (preferida).** Subir el código a GitHub (lo hace el equipo; si es privado, dar acceso al docente). El `.gitignore` ya excluye `target/`, `.env`, `.idea/` y archivos de IDE.
2. **Archivo ZIP.** Contiene el código sin `target/`, sin `.env` y sin carpetas de IDE. Quien lo reciba crea su propio `.env` a partir de `.env.example`, ejecuta `docker compose up -d` y `mvnw spring-boot:run`.

Los datos de demostración se pueden volver a cargar con el volcado `datos/demo/liga_dimayor_demo.sql` (ver la sección 7).

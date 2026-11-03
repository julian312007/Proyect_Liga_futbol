# Guía para ejecutar el proyecto (docente)

Proyecto: **Sistema de Gestión de una Liga de Fútbol** (Santiago Panesso y Julián Valencia, UNIAJC).
API REST en Java 21 con Spring Boot 4.1, PostgreSQL 17 y Redis 7. Se ejecuta con IntelliJ IDEA y Docker Desktop.

## 1. Requisitos

| Qué | Versión / nota |
|---|---|
| IntelliJ IDEA | Cualquier versión reciente |
| JDK | **21** (IntelliJ: *File > Project Structure > SDKs > Add SDK > Download JDK > 21*) |
| Docker Desktop | Instalado y **abierto** antes de empezar |
| Memoria | Recomendado 8 GB de RAM o más. Con 6 GB funciona, pero lento |
| Cuenta de Google | El correo que el equipo autorizó como usuario de prueba |

## 2. Descomprimir el proyecto

1. Descomprime el ZIP en una carpeta **sin tildes ni espacios**, por ejemplo `C:\proyectos\Proyect_Liga_futbol`.
2. Dentro debe verse el archivo `pom.xml` en la raíz.

## 3. Crear el archivo `.env`

El `.env` **no viene en el ZIP** (contiene claves privadas). Lo creas tú a partir de la plantilla:

1. Copia `.env.example` en la misma carpeta y renómbralo a `.env`.
2. Ábrelo y completa estas variables:

| Variable | Qué poner |
|---|---|
| `DB_PASSWORD` | Una contraseña cualquiera para la base de datos local (por ejemplo `liga2026segura`) |
| `JWT_SECRET` | Una cadena aleatoria de **al menos 32 caracteres** (ver abajo cómo generarla) |
| `GOOGLE_CLIENT_ID` | El ID de cliente de Google que te enviaron los autores por un canal privado. Termina en `.apps.googleusercontent.com` |
| `LIGA_ADMIN_EMAIL` | El correo de Google que será administrador. Puede ser el tuyo |

Para generar `JWT_SECRET` en PowerShell (copia la línea completa):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Pega el resultado como valor de `JWT_SECRET`.

Las demás variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `REDIS_HOST`, `REDIS_PORT`, `SERVER_PORT`) déjalas como están.

> **No subas el `.env` a ningún repositorio ni lo compartas.** Está en `.gitignore`, pero conviene revisarlo.

## 4. Abrir el proyecto en IntelliJ

1. *File > Open* → elige la carpeta que contiene `pom.xml` → **Trust Project**.
2. Espera a que IntelliJ descargue las dependencias de Maven (barra inferior). La primera vez tarda varios minutos.
3. *File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors* → marca **Enable annotation processing** (necesario para Lombok y MapStruct).
4. *File > Project Structure > Project* → SDK **21**, Language level **21**.

## 5. Levantar PostgreSQL y Redis

Con Docker Desktop abierto, en la terminal de IntelliJ (*View > Tool Windows > Terminal*):

```bash
docker compose up -d
```

Espera a que los dos contenedores digan `healthy`:

```bash
docker compose ps
```

## 6. Ejecutar la aplicación

1. Abre `src/main/java/co/edu/uniajc/liga/LigaFutbolApplication.java`.
2. Pulsa el triángulo verde junto a `main` (**Run**).
3. Espera a que aparezca `Started LigaFutbolApplication`. Tarda 1 a 3 minutos la primera vez, porque Flyway crea las tablas.

Si aparece *Could not resolve placeholder 'DB_PASSWORD'* o similar, el `.env` no está en la raíz del proyecto. Revisa el paso 3.

## 7. Entrar al sistema

1. Abre en el navegador: **http://localhost:8080/login-google.html**
2. Inicia sesión con el correo de Google autorizado.
3. Copia el token que muestra la página.
4. Abre **http://localhost:8080/swagger-ui.html**, pulsa **Authorize**, pega el token **sin escribir «Bearer»** y confirma.

Con el correo que pusiste en `LIGA_ADMIN_EMAIL` entras como **ADMIN** (puedes crear y modificar datos). Cualquier otro correo entra como **USER** (solo consulta).

## 8. Cargar los datos de la Liga dimayor (opcional)

Con la aplicación ya arrancada una vez (así Flyway crea las tablas), en la terminal del proyecto:

```bash
docker cp datos/demo/liga_dimayor_demo.sql liga-postgres:/tmp/demo.sql
docker exec liga-postgres psql -U <DB_USER> -d <DB_NAME> -f /tmp/demo.sql
```

Reemplaza `<DB_USER>` y `<DB_NAME>` por los valores de tu `.env` (por defecto `liga_user` y `liga`). Para empezar de cero: `docker compose down -v`.

## 9. Pruebas

En la terminal de IntelliJ:

```bash
./mvnw test      # pruebas unitarias, no necesitan Docker
./mvnw verify    # además las de integración, necesitan Docker Desktop abierto
```

En Windows usa `mvnw.cmd` en lugar de `./mvnw`.

> **Nota honesta:** las pruebas de las fases de dominio (catálogos, equipos, partidos, eventos y estadísticas) están escritas y compilan, pero **todavía no se han ejecutado completas con Maven**. Si alguna falla, es normal en esta etapa; avísanos con el mensaje de error.

## 10. Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | Falta el `.env` o la variable está vacía. Ver el paso 3 |
| `JWT_SECRET es demasiado corto` | Usa al menos 32 caracteres |
| `Cannot connect to the Docker daemon` | Abre Docker Desktop y espera a que diga *Engine running* |
| `Timed out waiting for log output ... database system is ready` | Poca memoria. Cierra programas y repite `./mvnw verify` |
| Error 401 al entrar con Google | Tu correo no está en *Usuarios de prueba* del proyecto de Google. Pide a los autores que lo agreguen |
| Error en el botón de Google | Pide a los autores revisar que `http://localhost:8080` esté autorizado |
| `Port 8080 was already in use` | Cierra la otra aplicación o cambia `SERVER_PORT` en el `.env` |

## 11. Detener todo

- Parar la aplicación: botón rojo de IntelliJ.
- Parar la base de datos (conserva los datos): `docker compose down`.
- Parar y borrar los datos: `docker compose down -v`.

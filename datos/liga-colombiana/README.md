# Datos de ejemplo: Liga colombiana (Liga BetPlay Dimayor, temporada 2026)

Insumo para poblar la base de datos de desarrollo. **Todavía no se carga en ninguna base**: las tablas
llegan por fases (ver el Anexo B de `ESTATUS.md`).

## Qué es real y qué no

| Dato | Origen |
|---|---|
| Los 20 clubes, su ciudad, año de fundación, estadio y capacidad | **Real**, verificado en fuentes públicas (abajo) |
| Entrenadores, jugadores y árbitros | **Ficticios**, se generarán con un script. No se usan nombres de personas reales |
| Partidos, resultados, goles y tarjetas | **Ficticios**, generados con un script coherente con las reglas de negocio |

## Archivo

`clubes_estadios.csv` (UTF-8, separado por comas). Una fila por club; 20 clubes y 17 estadios distintos,
porque tres estadios son compartidos:

- Atanasio Girardot: Atlético Nacional e Independiente Medellín.
- Nemesio Camacho El Campín: Independiente Santa Fe y Millonarios.
- Metropolitano de Techo: Fortaleza CEIF e Internacional de Bogotá.

El generador de calendario debe respetar RN-06 (un estadio no puede tener dos partidos a la misma fecha y hora).

## Cómo se eligieron los datos

- **Clubes:** los 20 de la tabla de la Liga BetPlay 2026-II (fecha 17 en curso al 2 de noviembre de 2026).
- **Año de fundación:** la columna "Fundación" del *Anexo: Clubes de fútbol de Colombia por fundación* de
  Wikipedia, no la fecha legal. Donde ambas difieren, la nota del CSV lo indica
  (Once Caldas, Jaguares de Córdoba, Internacional de Bogotá, América de Cali).
- **Capacidad:** una sola fuente para los 20 (tabla de la Liga DIMAYOR en Wikipedia en inglés), para que sean
  comparables entre sí. Es aproximada: **otras fuentes dan cifras distintas** (por ejemplo, para Atlético Nacional
  entre 44.412 y 46.000; para Deportivo Pasto entre 19.000 y 25.000). Para pruebas no importa; para un dato oficial
  habría que consultar al club o a Dimayor.
- **Águilas Doradas:** se registra el Alberto Grisales de Rionegro, su sede propia; en 2026-I usó el
  Cincuentenario de Medellín por obras.
- **La lista cambia cada año:** Boyacá Chicó aparece como descendido, y habrá otro descenso y dos ascensos.

## Fuentes (consultadas el 2 de noviembre de 2026)

- Wikipedia, [Anexo: Clubes de fútbol de Colombia por fundación](https://es.wikipedia.org/wiki/Anexo:Clubes_de_f%C3%BAtbol_de_Colombia_por_fundaci%C3%B3n)
- Wikipedia, [Anexo: Clubes de fútbol de Colombia](https://es.wikipedia.org/wiki/Anexo:Clubes_de_f%C3%BAtbol_de_Colombia)
- Wikipedia (inglés), [Liga DIMAYOR](https://en.wikipedia.org/wiki/Liga_DIMAYOR): estadios y capacidades
- Wikipedia, [Torneo Apertura 2026 (Colombia)](https://es.wikipedia.org/wiki/Torneo_Apertura_2026_(Colombia)): ciudad del estadio de Deportivo Cali y sede provisional de Águilas Doradas
- Claro Sports, [tabla de posiciones de la Liga BetPlay 2026-II](https://www.clarosports.com/colombia/liga-bet-play/clasificacion-liga-betplay-dimayor-2026-ii/): lista de los 20 clubes
- Win Sports, [Águilas Doradas: calendario y datos del club](https://www.winsports.co/futbol-colombiano/equipos/aguilas-doradas/calendario) y [El Colombiano](https://www.elcolombiano.com/deportes/futbol/aguilas-bragantino-previa-libertadores-DA23762919): fundación de Águilas Doradas (2008)

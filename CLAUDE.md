# Proyecto: Sistema de Gestión de una Liga de Fútbol

API REST en Java 21 con Spring Boot 4.1 (Maven), PostgreSQL y Redis. Equipo: Santiago Panesso y Julián Valencia
(Universidad Antonio José Camacho, Cali). Paquete base: `co.edu.uniajc.liga`.

## Al empezar cada sesión

1. Lee `ESTATUS.md`: es el estado real del proyecto. Continúa desde ahí. No dupliques su contenido en este archivo.
2. Lee `PROMPT_Liga_Futbol.md`: reglas de trabajo, diseño y plan por fases. Su **sección 0** (decisiones del
   2026-11-02) prevalece sobre el resto del documento.

## Reglas que más importan (el detalle está en el prompt)

- Se trabaja por fases. Al terminar una: compilar, correr las pruebas, actualizar `ESTATUS.md`, resumir en pocas
  líneas y **esperar confirmación** antes de pasar a la siguiente.
- Un elemento es `HECHO` solo si compila y tiene una prueba pasando. No declares éxito sin evidencia.
- Contexto antes de documentar (regla 1) y criterio técnico: señala riesgos de rendimiento, seguridad o diseño con
  pros, contras y una recomendación fundamentada.
- Nada de commits, push ni borrado de archivos si no se piden.
- Sin secretos en el repositorio. El archivo `.env` nunca se sube (está en `.gitignore`).
- Dominio en español; sufijos técnicos en inglés; Javadoc, comentarios y mensajes de error en español.

## Comandos

- Infraestructura local: `docker compose up -d` (PostgreSQL y Redis; requiere Docker Desktop abierto).
- Pruebas unitarias: `mvnw test` (no necesitan Docker). Con integración: `mvnw verify` (necesita Docker).
- Arranque: `mvnw spring-boot:run`; Swagger UI en `http://localhost:8080/swagger-ui.html`.
- En Windows se usa `mvnw.cmd`. Si el wrapper falla por falta de `.mvn/wrapper/maven-wrapper.properties`, se puede crear
  con el contenido de la sección 10 de `ESTATUS.md` o usar el Maven de IntelliJ.

-- V1: línea base del esquema.
-- No crea tablas a propósito: cada fase añade las suyas en migraciones nuevas (V2, V3, ...)
-- junto con las entidades JPA correspondientes. Una migración ya aplicada no se edita nunca.
-- La sentencia siguiente solo deja constancia de que Flyway corrió sobre la base.
SELECT 1;

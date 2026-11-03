/**
 * Implementaciones de los servicios: aquí viven <b>todas</b> las reglas de negocio.
 *
 * <p><b>Debe:</b> usar {@code @Transactional} (con {@code readOnly = true} en consultas), declarar
 * {@code @Cacheable/@CacheEvict} en las consultas agregadas, lanzar las excepciones de dominio
 * de {@code co.edu.uniajc.liga.exception} y recibir sus dependencias por constructor. Es la única capa
 * que usa repositorios y mappers.
 *
 * <p><b>No debe:</b> conocer HTTP. Tampoco llamar a sus propios métodos con {@code @Cacheable}
 * desde la misma clase: esa llamada no pasa por el proxy y la anotación no se aplica.
 */
package co.edu.uniajc.liga.service.impl;

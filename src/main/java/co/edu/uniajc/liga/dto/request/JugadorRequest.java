package co.edu.uniajc.liga.dto.request;

import co.edu.uniajc.liga.entity.PosicionJugador;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos para crear o reemplazar un jugador ({@code POST} y {@code PUT /api/v1/jugadores}). El equipo llega
 * por id; en un {@code PUT}, cambiar {@code equipoId} mueve al jugador a otro equipo (RN-02).
 *
 * <p>Que la fecha de nacimiento esté en el pasado y que el dorsal esté libre en el equipo son reglas de negocio
 * que valida {@code JugadorServiceImpl} (422 y 409), no Bean Validation.
 *
 * @param nombre          nombre del jugador
 * @param fechaNacimiento fecha de nacimiento ({@code yyyy-MM-dd}); debe ser anterior a hoy
 * @param posicion        PORTERO, DEFENSA, MEDIOCAMPISTA o DELANTERO
 * @param dorsal          número de camiseta, de 1 a 99; único dentro del equipo
 * @param equipoId        id del equipo al que pertenece
 */
public record JugadorRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        LocalDate fechaNacimiento,

        @NotNull(message = "La posición es obligatoria")
        PosicionJugador posicion,

        @NotNull(message = "El dorsal es obligatorio")
        @Min(value = 1, message = "El dorsal debe estar entre 1 y 300")
        @Max(value = 300, message = "El dorsal debe estar entre 1 y 300")
        Integer dorsal,

        @NotNull(message = "El equipo es obligatorio")
        @Positive(message = "El id del equipo debe ser mayor que cero")
        Long equipoId) {
}

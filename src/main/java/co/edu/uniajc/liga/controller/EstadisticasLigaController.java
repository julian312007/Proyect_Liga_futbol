package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.response.GoleadorResponse;
import co.edu.uniajc.liga.dto.response.PosicionEquipoResponse;
import co.edu.uniajc.liga.dto.response.SancionJugadorResponse;
import co.edu.uniajc.liga.dto.response.SuspendidoResponse;
import co.edu.uniajc.liga.service.GoleadoresService;
import co.edu.uniajc.liga.service.SancionService;
import co.edu.uniajc.liga.service.TablaPosicionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estadísticas derivadas de una liga: tabla de posiciones, goleadores y sanciones (F4, F5 y F7). Solo
 * lectura: delega en los servicios, que calculan los resultados y manejan la caché.
 *
 * <p>Acceso: {@code USER} o {@code ADMIN} (la autenticación la exige {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/ligas")
@Tag(name = "Estadísticas de la liga", description = "Tabla de posiciones, goleadores y sanciones de una liga")
public class EstadisticasLigaController {

    private final TablaPosicionesService tablaPosicionesService;
    private final GoleadoresService goleadoresService;
    private final SancionService sancionService;

    public EstadisticasLigaController(TablaPosicionesService tablaPosicionesService,
                                      GoleadoresService goleadoresService,
                                      SancionService sancionService) {
        this.tablaPosicionesService = tablaPosicionesService;
        this.goleadoresService = goleadoresService;
        this.sancionService = sancionService;
    }

    @GetMapping("/{ligaId}/tabla-posiciones")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Tabla de posiciones",
            description = "PJ, PG, PE, PP, GF, GC, DG y Pts de cada equipo, con solo los partidos finalizados. "
                    + "Victoria 3 puntos, empate 1, derrota 0 (RN-01). Orden: puntos, diferencia de gol y goles a favor. "
                    + "Se guarda en caché 10 minutos y se invalida al finalizar un partido de la liga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tabla de posiciones"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "La liga no existe")
    })
    public List<PosicionEquipoResponse> tablaPosiciones(@PathVariable("ligaId") Long ligaId) {
        return tablaPosicionesService.obtenerTabla(ligaId);
    }

    @GetMapping("/{ligaId}/goleadores")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Tabla de goleadores",
            description = "Goles por jugador con solo los partidos finalizados. Suman los goles normales y de penal; "
                    + "el autogol no. Se guarda en caché 10 minutos y se invalida al finalizar un partido de la liga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tabla de goleadores"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "La liga no existe")
    })
    public List<GoleadorResponse> goleadores(@PathVariable("ligaId") Long ligaId) {
        return goleadoresService.obtenerGoleadores(ligaId);
    }

    @GetMapping("/{ligaId}/sanciones")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Jugadores con tarjetas",
            description = "Jugadores de la liga con al menos una tarjeta, con su conteo de amarillas y rojas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugadores sancionados"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "La liga no existe")
    })
    public List<SancionJugadorResponse> sanciones(@PathVariable("ligaId") Long ligaId) {
        return sancionService.listarSanciones(ligaId);
    }

    @GetMapping("/{ligaId}/sanciones/suspendidos")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Suspendidos de una jornada",
            description = "Jugadores que no pueden jugar los partidos de la jornada por una expulsión en el partido "
                    + "anterior de su equipo (RN-10).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugadores suspendidos (lista vacía si no hay)"),
            @ApiResponse(responseCode = "400", description = "Falta la jornada o es menor que 1"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "La liga no existe")
    })
    public List<SuspendidoResponse> suspendidos(@PathVariable("ligaId") Long ligaId,
                                                @RequestParam("jornada") @Min(1) Integer jornada) {
        return sancionService.listarSuspendidos(ligaId, jornada);
    }
}

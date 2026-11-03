package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoHistorialResponse;
import co.edu.uniajc.liga.service.HistorialEquipoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de partidos de un equipo (F6). Solo lectura. Acceso: {@code USER} o {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/equipos")
@Tag(name = "Historial de equipos", description = "Partidos de un equipo, de local o de visitante")
public class HistorialEquipoController {

    private final HistorialEquipoService historialEquipoService;

    public HistorialEquipoController(HistorialEquipoService historialEquipoService) {
        this.historialEquipoService = historialEquipoService;
    }

    @GetMapping("/{equipoId}/partidos")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Historial de partidos del equipo",
            description = "Partidos del equipo en cualquier estado, paginados (?page=&size=&sort=). Por defecto los más "
                    + "recientes primero; se puede ordenar por fechaHora, jornada o id. El resultado (victoria, empate "
                    + "o derrota) solo se informa en los partidos finalizados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de partidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe")
    })
    public PaginaResponse<PartidoHistorialResponse> historial(@PathVariable("equipoId") Long equipoId,
                                                              Pageable pageable) {
        return historialEquipoService.obtenerHistorial(equipoId, pageable);
    }
}

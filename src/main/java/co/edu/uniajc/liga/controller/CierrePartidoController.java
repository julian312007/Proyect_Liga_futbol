package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.FinalizarPartidoRequest;
import co.edu.uniajc.liga.dto.response.ResultadoPartidoResponse;
import co.edu.uniajc.liga.service.CierrePartidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cierre de un partido (F3). Delgado: valida con {@code @Valid}, exige {@code ADMIN} y delega en
 * {@link CierrePartidoService}, donde viven RN-11 y RN-12 y la invalidación de las cachés.
 */
@RestController
@RequestMapping("/api/v1/partidos/{partidoId}")
@Tag(name = "Cierre de partido", description = "Finalización de un partido con su marcador")
public class CierrePartidoController {

    private final CierrePartidoService cierrePartidoService;

    public CierrePartidoController(CierrePartidoService cierrePartidoService) {
        this.cierrePartidoService = cierrePartidoService;
    }

    /** Es una acción sobre un partido existente (no crea un recurso), por eso responde 200 y no 201. */
    @PostMapping("/finalizar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Finalizar un partido",
            description = "Solo desde EN_JUEGO. El marcador enviado debe coincidir con los goles registrados (RN-11): "
                    + "goles NORMAL y PENAL de cada equipo más los autogoles del rival. Un partido FINALIZADO ya no "
                    + "cambia (RN-12). Invalida la tabla de posiciones y los goleadores de la liga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partido finalizado"),
            @ApiResponse(responseCode = "400", description = "Marcador ausente o negativo"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido no existe"),
            @ApiResponse(responseCode = "409", description = "RN-12: ya está FINALIZADO; PARTIDO_ESTADO_INVALIDO: "
                    + "todavía no está EN_JUEGO"),
            @ApiResponse(responseCode = "422", description = "RN-11: el marcador no coincide con los goles registrados")
    })
    public ResultadoPartidoResponse finalizar(@PathVariable Long partidoId,
                                              @Valid @RequestBody FinalizarPartidoRequest request) {
        return cierrePartidoService.finalizar(partidoId, request);
    }
}

package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.TarjetaRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.TarjetaResponse;
import co.edu.uniajc.liga.service.TarjetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Tarjetas de un partido (F3). Delgado: valida con {@code @Valid}, protege por rol y delega en
 * {@link TarjetaService}, donde viven RN-08, RN-09, RN-10 y RN-12.
 *
 * <p>{@code GET} lo pueden usar {@code USER} y {@code ADMIN}; registrar y eliminar, solo {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/partidos/{partidoId}/tarjetas")
@Tag(name = "Tarjetas", description = "Registro, consulta y eliminación de las tarjetas de un partido")
public class TarjetaController {

    private final TarjetaService tarjetaService;

    public TarjetaController(TarjetaService tarjetaService) {
        this.tarjetaService = tarjetaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar una tarjeta",
            description = "Solo con el partido EN_JUEGO. La segunda amarilla a un jugador genera además una ROJA en "
                    + "el mismo minuto (RN-09); a un jugador ya expulsado no se le admiten más tarjetas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarjeta registrada (la roja automática se ve en el listado)"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (minuto fuera de 1-130, tipo desconocido...)"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido o el jugador no existen"),
            @ApiResponse(responseCode = "409", description = "El partido no está EN_JUEGO (RN-12 si ya está FINALIZADO)"),
            @ApiResponse(responseCode = "422", description = "RN-08: jugador de otro equipo; RN-10: jugador suspendido; "
                    + "TARJETA_JUGADOR_EXPULSADO: ya fue expulsado en este partido")
    })
    public ResponseEntity<TarjetaResponse> registrar(@PathVariable Long partidoId,
                                                     @Valid @RequestBody TarjetaRequest request) {
        TarjetaResponse creada = tarjetaService.registrar(partidoId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar las tarjetas de un partido",
            description = "Paginado (?page=&size=&sort=). Orden por defecto: minuto. Se puede ordenar por id, minuto o tipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de tarjetas"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El partido no existe")
    })
    public PaginaResponse<TarjetaResponse> listar(@PathVariable Long partidoId, @ParameterObject Pageable pageable) {
        return tarjetaService.listar(partidoId, pageable);
    }

    @DeleteMapping("/{tarjetaId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar una tarjeta",
            description = "Solo con el partido EN_JUEGO: un partido FINALIZADO no admite cambios (RN-12). Si era la "
                    + "segunda amarilla, la roja automática se elimina por separado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tarjeta eliminada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido o la tarjeta no existen"),
            @ApiResponse(responseCode = "409", description = "El partido no está EN_JUEGO (RN-12 si ya está FINALIZADO)")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long partidoId, @PathVariable Long tarjetaId) {
        tarjetaService.eliminar(partidoId, tarjetaId);
        return ResponseEntity.noContent().build();
    }
}

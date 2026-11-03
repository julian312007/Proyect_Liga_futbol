package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.GolRequest;
import co.edu.uniajc.liga.dto.response.GolResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.GolService;
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
 * Goles de un partido (F3). Delgado: valida con {@code @Valid}, protege por rol y delega en
 * {@link GolService}, donde viven RN-08, RN-10 y RN-12.
 *
 * <p>{@code GET} lo pueden usar {@code USER} y {@code ADMIN}; registrar y eliminar, solo {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/partidos/{partidoId}/goles")
@Tag(name = "Goles", description = "Registro, consulta y eliminación de los goles de un partido")
public class GolController {

    private final GolService golService;

    public GolController(GolService golService) {
        this.golService = golService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar un gol",
            description = "Solo con el partido EN_JUEGO. El jugador debe ser de uno de los dos equipos (RN-08) y no "
                    + "estar suspendido (RN-10). Un AUTOGOL suma al equipo contrario.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Gol registrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (minuto fuera de 1-130, tipo desconocido...)"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido o el jugador no existen"),
            @ApiResponse(responseCode = "409", description = "El partido no está EN_JUEGO (RN-12 si ya está FINALIZADO)"),
            @ApiResponse(responseCode = "422", description = "RN-08: jugador de otro equipo; RN-10: jugador suspendido")
    })
    public ResponseEntity<GolResponse> registrar(@PathVariable Long partidoId,
                                                 @Valid @RequestBody GolRequest request) {
        GolResponse creado = golService.registrar(partidoId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar los goles de un partido",
            description = "Paginado (?page=&size=&sort=). Orden por defecto: minuto. Se puede ordenar por id o minuto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de goles"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El partido no existe")
    })
    public PaginaResponse<GolResponse> listar(@PathVariable Long partidoId, @ParameterObject Pageable pageable) {
        return golService.listar(partidoId, pageable);
    }

    @DeleteMapping("/{golId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un gol",
            description = "Solo con el partido EN_JUEGO: un partido FINALIZADO no admite cambios (RN-12).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Gol eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido o el gol no existen"),
            @ApiResponse(responseCode = "409", description = "El partido no está EN_JUEGO (RN-12 si ya está FINALIZADO)")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long partidoId, @PathVariable Long golId) {
        golService.eliminar(partidoId, golId);
        return ResponseEntity.noContent().build();
    }
}

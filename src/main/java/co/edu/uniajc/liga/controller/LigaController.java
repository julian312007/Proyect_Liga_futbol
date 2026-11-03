package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.LigaRequest;
import co.edu.uniajc.liga.dto.response.LigaResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.LigaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * CRUD de ligas. Delgado: valida, autoriza y delega en {@link LigaService}. {@code GET} para {@code USER} y
 * {@code ADMIN}; el resto, solo {@code ADMIN}. Las consultas derivadas de una liga (tabla de posiciones,
 * goleadores, sanciones) las publica la Fase 6 en sus propios controladores.
 */
@RestController
@RequestMapping("/api/v1/ligas")
@Tag(name = "Ligas", description = "Catálogo de ligas")
public class LigaController {

    private final LigaService ligaService;

    public LigaController(LigaService ligaService) {
        this.ligaService = ligaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar ligas",
            description = "Página de ligas. Parámetros: page (desde 0), size (máximo 100) y sort "
                    + "(id, nombre, temporada, fechaInicio o fechaFin; por defecto, las más recientes primero).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de ligas"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<LigaResponse> listar(@ParameterObject Pageable pageable) {
        return ligaService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Obtener una liga")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La liga"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "La liga no existe")
    })
    public LigaResponse obtener(@PathVariable Long id) {
        return ligaService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear una liga",
            description = "Nombre y temporada obligatorios; la fecha de fin debe ser posterior a la de inicio; "
                    + "no puede repetirse la pareja (nombre, temporada).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Liga creada; la cabecera Location apunta a ella"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "409", description = "Ya existe esa liga en esa temporada (LIGA_DUPLICADA)"),
            @ApiResponse(responseCode = "422", description = "La fecha de fin no es posterior a la de inicio "
                    + "(LIGA_FECHAS_INVALIDAS)")
    })
    public ResponseEntity<LigaResponse> crear(@Valid @RequestBody LigaRequest request) {
        LigaResponse creada = ligaService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar una liga",
            description = "Mismas reglas que al crear; la unicidad no cuenta a la propia liga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liga actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "La liga no existe"),
            @ApiResponse(responseCode = "409", description = "Ya existe otra liga con ese nombre y temporada "
                    + "(LIGA_DUPLICADA)"),
            @ApiResponse(responseCode = "422", description = "La fecha de fin no es posterior a la de inicio "
                    + "(LIGA_FECHAS_INVALIDAS)")
    })
    public LigaResponse actualizar(@PathVariable Long id, @Valid @RequestBody LigaRequest request) {
        return ligaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar una liga",
            description = "No se elimina si tiene equipos o partidos. Invalida sus cachés de estadísticas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Liga eliminada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "La liga no existe"),
            @ApiResponse(responseCode = "409", description = "La liga tiene equipos o partidos")
    })
    public void eliminar(@PathVariable Long id) {
        ligaService.eliminar(id);
    }
}

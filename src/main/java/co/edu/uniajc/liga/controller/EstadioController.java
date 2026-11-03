package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.EstadioRequest;
import co.edu.uniajc.liga.dto.response.EstadioResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.EstadioService;
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
 * CRUD de estadios: el controlador independiente de referencia del requisito D-4 (protección por rol y reglas
 * de negocio en el servicio).
 *
 * <p>Delgado a propósito: valida con {@code @Valid} (EST-03), autoriza con {@code @PreAuthorize} y delega todo
 * en {@link EstadioService}, que aplica EST-01 y EST-02. {@code GET} lo pueden usar {@code USER} y
 * {@code ADMIN}; {@code POST}, {@code PUT} y {@code DELETE}, solo {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/estadios")
@Tag(name = "Estadios", description = "Catálogo de estadios (controlador de referencia, D-4). Reglas EST-01 a EST-03")
public class EstadioController {

    private final EstadioService estadioService;

    public EstadioController(EstadioService estadioService) {
        this.estadioService = estadioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar estadios",
            description = "Página de estadios. Parámetros: page (desde 0), size (máximo 100) y sort "
                    + "(id, nombre, ciudad o capacidad; por defecto, nombre).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de estadios"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<EstadioResponse> listar(@ParameterObject Pageable pageable) {
        return estadioService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Obtener un estadio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El estadio"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El estadio no existe")
    })
    public EstadioResponse obtener(@PathVariable Long id) {
        return estadioService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un estadio",
            description = "EST-01: el nombre se normaliza y es único por ciudad sin distinguir mayúsculas. "
                    + "EST-03: la ciudad es obligatoria y la capacidad mayor que cero.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estadio creado; la cabecera Location apunta a él"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (EST-03)"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "409", description = "Ya existe un estadio con ese nombre en la ciudad (EST-01)")
    })
    public ResponseEntity<EstadioResponse> crear(@Valid @RequestBody EstadioRequest request) {
        EstadioResponse creado = estadioService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar un estadio",
            description = "Mismas reglas que al crear; EST-01 no cuenta al propio estadio como duplicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadio actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (EST-03)"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El estadio no existe"),
            @ApiResponse(responseCode = "409", description = "Ya existe otro estadio con ese nombre en la ciudad (EST-01)")
    })
    public EstadioResponse actualizar(@PathVariable Long id, @Valid @RequestBody EstadioRequest request) {
        return estadioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un estadio",
            description = "EST-02: no se elimina si es estadio local de algún equipo o tiene partidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estadio eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El estadio no existe"),
            @ApiResponse(responseCode = "409", description = "El estadio tiene equipos o partidos (EST-02)")
    })
    public void eliminar(@PathVariable Long id) {
        estadioService.eliminar(id);
    }
}

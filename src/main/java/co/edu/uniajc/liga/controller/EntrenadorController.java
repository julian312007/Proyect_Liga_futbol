package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.EntrenadorRequest;
import co.edu.uniajc.liga.dto.response.EntrenadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.EntrenadorService;
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
 * CRUD de entrenadores. Delgado: valida, autoriza y delega en {@link EntrenadorService}. {@code GET} para
 * {@code USER} y {@code ADMIN}; el resto, solo {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/entrenadores")
@Tag(name = "Entrenadores", description = "Catálogo de entrenadores")
public class EntrenadorController {

    private final EntrenadorService entrenadorService;

    public EntrenadorController(EntrenadorService entrenadorService) {
        this.entrenadorService = entrenadorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar entrenadores",
            description = "Página de entrenadores. Parámetros: page (desde 0), size (máximo 100) y sort "
                    + "(id, nombre o nacionalidad; por defecto, nombre).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de entrenadores"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<EntrenadorResponse> listar(@ParameterObject Pageable pageable) {
        return entrenadorService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Obtener un entrenador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El entrenador"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El entrenador no existe")
    })
    public EntrenadorResponse obtener(@PathVariable Long id) {
        return entrenadorService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un entrenador", description = "El nombre es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrenador creado; la cabecera Location apunta a él"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN")
    })
    public ResponseEntity<EntrenadorResponse> crear(@Valid @RequestBody EntrenadorRequest request) {
        EntrenadorResponse creado = entrenadorService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar un entrenador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrenador actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El entrenador no existe")
    })
    public EntrenadorResponse actualizar(@PathVariable Long id, @Valid @RequestBody EntrenadorRequest request) {
        return entrenadorService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un entrenador", description = "No se elimina si dirige a un equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entrenador eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El entrenador no existe"),
            @ApiResponse(responseCode = "409", description = "El entrenador dirige a un equipo")
    })
    public void eliminar(@PathVariable Long id) {
        entrenadorService.eliminar(id);
    }
}

package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.ArbitroRequest;
import co.edu.uniajc.liga.dto.response.ArbitroResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.ArbitroService;
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
 * CRUD de árbitros. Delgado: valida, autoriza y delega en {@link ArbitroService}. {@code GET} para
 * {@code USER} y {@code ADMIN}; el resto, solo {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/arbitros")
@Tag(name = "Árbitros", description = "Catálogo de árbitros")
public class ArbitroController {

    private final ArbitroService arbitroService;

    public ArbitroController(ArbitroService arbitroService) {
        this.arbitroService = arbitroService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar árbitros",
            description = "Página de árbitros. Parámetros: page (desde 0), size (máximo 100) y sort "
                    + "(id, nombre, nacionalidad o aniosExperiencia; por defecto, nombre).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de árbitros"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<ArbitroResponse> listar(@ParameterObject Pageable pageable) {
        return arbitroService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Obtener un árbitro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El árbitro"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El árbitro no existe")
    })
    public ArbitroResponse obtener(@PathVariable Long id) {
        return arbitroService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un árbitro", description = "Nombre obligatorio; años de experiencia de cero o más.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Árbitro creado; la cabecera Location apunta a él"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN")
    })
    public ResponseEntity<ArbitroResponse> crear(@Valid @RequestBody ArbitroRequest request) {
        ArbitroResponse creado = arbitroService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar un árbitro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Árbitro actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El árbitro no existe")
    })
    public ArbitroResponse actualizar(@PathVariable Long id, @Valid @RequestBody ArbitroRequest request) {
        return arbitroService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un árbitro", description = "No se elimina si tiene partidos asignados.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Árbitro eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El árbitro no existe"),
            @ApiResponse(responseCode = "409", description = "El árbitro tiene partidos asignados")
    })
    public void eliminar(@PathVariable Long id) {
        arbitroService.eliminar(id);
    }
}

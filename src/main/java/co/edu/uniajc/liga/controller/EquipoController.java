package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.EquipoRequest;
import co.edu.uniajc.liga.dto.response.CuerpoTecnicoResponse;
import co.edu.uniajc.liga.dto.response.EquipoResponse;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.EquipoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * CRUD de equipos más su plantel y su cuerpo técnico (F1 y F6).
 *
 * <p>Delgado a propósito: valida con {@code @Valid}, comprueba el rol con {@code @PreAuthorize} y delega todo
 * en {@link EquipoService}. {@code GET} lo pueden usar {@code USER} y {@code ADMIN}; crear, editar y borrar
 * son solo de {@code ADMIN}. La autenticación (401) la decide {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/equipos")
@Tag(name = "Equipos", description = "Equipos de una liga, con su plantel y su cuerpo técnico")
public class EquipoController {

    private final EquipoService equipoService;

    public EquipoController(EquipoService equipoService) {
        this.equipoService = equipoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar equipos",
            description = "Página de equipos. Se puede ordenar por id, nombre, ciudad o anioFundacion "
                    + "(por ejemplo sort=nombre,asc); otro campo se ignora. Tamaño máximo de página: 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de equipos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<EquipoResponse> listar(@ParameterObject Pageable pageable) {
        return equipoService.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Ver un equipo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El equipo"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe")
    })
    public EquipoResponse obtener(@PathVariable Long id) {
        return equipoService.obtener(id);
    }

    @GetMapping("/{id}/plantel")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Plantel del equipo",
            description = "Página con los jugadores del equipo, por defecto ordenados por dorsal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de jugadores del equipo"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe")
    })
    public PaginaResponse<JugadorResponse> plantel(@PathVariable Long id, @ParameterObject Pageable pageable) {
        return equipoService.plantel(id, pageable);
    }

    @GetMapping("/{id}/cuerpo-tecnico")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Cuerpo técnico del equipo", description = "El entrenador que dirige al equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuerpo técnico del equipo"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe")
    })
    public CuerpoTecnicoResponse cuerpoTecnico(@PathVariable Long id) {
        return equipoService.cuerpoTecnico(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un equipo",
            description = "Solo ADMIN. Responde 201 con la cabecera Location del nuevo equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipo creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "La liga, el estadio o el entrenador no existen"),
            @ApiResponse(responseCode = "409", description = "Nombre repetido en la liga (EQUIPO_DUPLICADO) o "
                    + "entrenador ya asignado a otro equipo (RN-03)"),
            @ApiResponse(responseCode = "422", description = "Año de fundación posterior al actual "
                    + "(ANIO_FUNDACION_FUTURO)")
    })
    public ResponseEntity<EquipoResponse> crear(@Valid @RequestBody EquipoRequest request) {
        EquipoResponse creado = equipoService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar un equipo", description = "Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El equipo, la liga, el estadio o el entrenador no existen"),
            @ApiResponse(responseCode = "409", description = "Nombre repetido en la liga (EQUIPO_DUPLICADO) o "
                    + "entrenador ya asignado a otro equipo (RN-03)"),
            @ApiResponse(responseCode = "422", description = "Año de fundación posterior al actual "
                    + "(ANIO_FUNDACION_FUTURO)")
    })
    public EquipoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EquipoRequest request) {
        return equipoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un equipo",
            description = "Solo ADMIN. 409 si el equipo tiene jugadores o partidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Equipo eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe"),
            @ApiResponse(responseCode = "409", description = "El equipo tiene datos dependientes")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        equipoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

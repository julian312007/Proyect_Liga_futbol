package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.JugadorRequest;
import co.edu.uniajc.liga.dto.response.JugadorResponse;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.service.JugadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * CRUD de jugadores (F1). El plantel de un equipo también se consulta en {@code GET /equipos/{id}/plantel}.
 *
 * <p>Delgado a propósito: valida con {@code @Valid}, comprueba el rol con {@code @PreAuthorize} y delega todo
 * en {@link JugadorService}. {@code GET} lo pueden usar {@code USER} y {@code ADMIN}; crear, editar y borrar
 * son solo de {@code ADMIN}. La autenticación (401) la decide {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/jugadores")
@Tag(name = "Jugadores", description = "Jugadores de los equipos")
public class JugadorController {

    private final JugadorService jugadorService;

    public JugadorController(JugadorService jugadorService) {
        this.jugadorService = jugadorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Listar jugadores",
            description = "Página de jugadores. Con equipoId solo los de ese equipo. Se puede ordenar por id, "
                    + "nombre, dorsal, posicion o fechaNacimiento (por ejemplo sort=dorsal,asc); otro campo se "
                    + "ignora. Tamaño máximo de página: 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de jugadores"),
            @ApiResponse(responseCode = "400", description = "equipoId con formato incorrecto"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El equipo indicado en equipoId no existe")
    })
    public PaginaResponse<JugadorResponse> listar(
            @Parameter(description = "Filtra por el id del equipo (opcional)") @RequestParam(required = false) Long equipoId,
            @ParameterObject Pageable pageable) {
        return jugadorService.listar(equipoId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Ver un jugador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El jugador"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El jugador no existe")
    })
    public JugadorResponse obtener(@PathVariable Long id) {
        return jugadorService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un jugador",
            description = "Solo ADMIN. Responde 201 con la cabecera Location del nuevo jugador.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Jugador creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (por ejemplo, dorsal fuera de 1 a 99)"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El equipo no existe"),
            @ApiResponse(responseCode = "409", description = "Dorsal ya ocupado en el equipo (DORSAL_DUPLICADO)"),
            @ApiResponse(responseCode = "422", description = "Fecha de nacimiento no anterior a hoy "
                    + "(FECHA_NACIMIENTO_FUTURA)")
    })
    public ResponseEntity<JugadorResponse> crear(@Valid @RequestBody JugadorRequest request) {
        JugadorResponse creado = jugadorService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar un jugador",
            description = "Solo ADMIN. Si cambia equipoId, el jugador se mueve al nuevo equipo (RN-02): no se "
                    + "duplica, y su dorsal debe estar libre allí.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jugador actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El jugador o el equipo no existen"),
            @ApiResponse(responseCode = "409", description = "Dorsal ya ocupado en el equipo (DORSAL_DUPLICADO)"),
            @ApiResponse(responseCode = "422", description = "Fecha de nacimiento no anterior a hoy "
                    + "(FECHA_NACIMIENTO_FUTURA)")
    })
    public JugadorResponse actualizar(@PathVariable Long id, @Valid @RequestBody JugadorRequest request) {
        return jugadorService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un jugador", description = "Solo ADMIN. 409 si tiene goles o tarjetas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Jugador eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El jugador no existe"),
            @ApiResponse(responseCode = "409", description = "El jugador tiene datos dependientes")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        jugadorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

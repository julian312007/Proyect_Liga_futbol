package co.edu.uniajc.liga.controller;

import co.edu.uniajc.liga.dto.request.PartidoRequest;
import co.edu.uniajc.liga.dto.response.PaginaResponse;
import co.edu.uniajc.liga.dto.response.PartidoResponse;
import co.edu.uniajc.liga.service.PartidoService;
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
 * Programación de partidos y calendario (F2 y F8).
 *
 * <p>Delgado a propósito: valida con {@code @Valid}, declara el rol con {@code @PreAuthorize} y delega todo en
 * {@link PartidoService}. {@code GET} lo pueden usar {@code USER} y {@code ADMIN}; crear, editar, borrar e
 * iniciar un partido es solo para {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/partidos")
@Tag(name = "Partidos", description = "Programación de partidos, calendario por liga, jornada, equipo o estadio, "
        + "y ciclo de vida (iniciar)")
public class PartidoController {

    private final PartidoService partidoService;

    public PartidoController(PartidoService partidoService) {
        this.partidoService = partidoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Calendario de partidos",
            description = "Lista paginada de partidos. Todos los filtros son opcionales y se combinan con 'y': "
                    + "ligaId, jornada, equipoId (como local o visitante) y estadioId. Se ordena por jornada y "
                    + "fecha; también acepta sort por id, jornada, fechaHora o estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de partidos"),
            @ApiResponse(responseCode = "400", description = "Algún filtro tiene un formato incorrecto"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public PaginaResponse<PartidoResponse> listar(
            @Parameter(description = "Id de la liga") @RequestParam(required = false) Long ligaId,
            @Parameter(description = "Número de jornada") @RequestParam(required = false) Integer jornada,
            @Parameter(description = "Id de un equipo: sus partidos como local o como visitante")
            @RequestParam(required = false) Long equipoId,
            @Parameter(description = "Id del estadio") @RequestParam(required = false) Long estadioId,
            @ParameterObject Pageable pageable) {
        return partidoService.listar(ligaId, jornada, equipoId, estadioId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(summary = "Ver un partido")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El partido"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El partido no existe")
    })
    public PartidoResponse obtener(@PathVariable Long id) {
        return partidoService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Programar un partido",
            description = "Crea el partido en estado PROGRAMADO con marcador 0-0. Valida RN-04 (equipos distintos), "
                    + "RN-05 (un partido por equipo y jornada), RN-06 (estadio libre) y RN-07 (árbitro libre).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Partido creado; la cabecera Location apunta a él"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "La liga, un equipo, el estadio o el árbitro no existen"),
            @ApiResponse(responseCode = "409", description = "Choque de jornada (RN-05) o de horario (RN-06, RN-07)"),
            @ApiResponse(responseCode = "422", description = "Un equipo no puede enfrentarse a sí mismo (RN-04)")
    })
    public ResponseEntity<PartidoResponse> crear(@Valid @RequestBody PartidoRequest request) {
        PartidoResponse creado = partidoService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reprogramar un partido",
            description = "Solo un partido PROGRAMADO. Vuelve a validar RN-04 a RN-07 excluyendo al propio partido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partido actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido, la liga, un equipo, el estadio o el árbitro no existen"),
            @ApiResponse(responseCode = "409", description = "Partido finalizado (RN-12) o en juego, o choque de jornada/horario"),
            @ApiResponse(responseCode = "422", description = "Un equipo no puede enfrentarse a sí mismo (RN-04)")
    })
    public PartidoResponse actualizar(@PathVariable Long id, @Valid @RequestBody PartidoRequest request) {
        return partidoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un partido",
            description = "Solo un partido PROGRAMADO. Uno finalizado (RN-12) o en juego no se elimina.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Partido eliminado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido no existe"),
            @ApiResponse(responseCode = "409", description = "Partido finalizado (RN-12) o en juego")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        partidoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/iniciar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Iniciar un partido",
            description = "Pasa el partido de PROGRAMADO a EN_JUEGO; desde cualquier otro estado responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partido en juego"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido"),
            @ApiResponse(responseCode = "403", description = "Se requiere el rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "El partido no existe"),
            @ApiResponse(responseCode = "409", description = "El partido no está PROGRAMADO (PARTIDO_ESTADO_INVALIDO)")
    })
    public PartidoResponse iniciar(@PathVariable Long id) {
        return partidoService.iniciar(id);
    }
}

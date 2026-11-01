package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.HabitatRequest;
import sv.edu.ues.fmp.flora.dto.response.HabitatResponse;
import sv.edu.ues.fmp.flora.service.HabitatService;

@Tag(name = "Hábitats", description = "Catálogo de hábitats donde crecen las especies")
@RestController
@RequestMapping("/api/habitats")
@RequiredArgsConstructor
public class HabitatController {

    private final HabitatService habitatService;

    @Operation(summary = "Listar hábitats",
            description = "Devuelve el catálogo completo. Con soloActivos=true omite "
                    + "los que fueron dados de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<HabitatResponse>> listar(
            @Parameter(description = "Si es true, excluye los hábitats desactivados")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<HabitatResponse> respuesta = soloActivos
                ? habitatService.listarActivos()
                : habitatService.listarTodos();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Buscar hábitats por nombre",
            description = "Coincidencia parcial y sin distinguir mayúsculas: 'bos' encuentra "
                    + "'Bosque'. Incluye los hábitats desactivados.")
    @ApiResponse(responseCode = "200",
            description = "Listado obtenido; vacío si ningún hábitat coincide")
    @ApiResponse(responseCode = "400",
            description = "Falta el parámetro nombre, o está vacío o solo contiene espacios")
    @GetMapping("/buscar")
    public ResponseEntity<List<HabitatResponse>> buscarPorNombre(
            @Parameter(description = "Texto a buscar dentro del nombre", example = "bosque")
            @RequestParam(name = "nombre") String nombre) {

        return ResponseEntity.ok(habitatService.buscarPorNombre(nombre));
    }

    @Operation(summary = "Obtener un hábitat por su id")
    @ApiResponse(responseCode = "200", description = "Hábitat encontrado")
    @ApiResponse(responseCode = "404", description = "No existe un hábitat con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<HabitatResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(habitatService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un hábitat",
            description = "El nombre es único en todo el historial, sin distinguir mayúsculas "
                    + "ni espacios sobrantes, y debe contener al menos una letra. No se "
                    + "reutiliza el nombre de un hábitat desactivado: para recuperarlo está "
                    + "PATCH /api/habitats/{id}/activar.")
    @ApiResponse(responseCode = "201", description = "Hábitat creado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "409",
            description = "Ya existe un hábitat con ese nombre. Si está activo, el mensaje lo "
                    + "indica sin más; si está desactivado, el mensaje incluye su id y la ruta "
                    + "PATCH para activarlo en lugar de crear uno nuevo.")
    @PreAuthorize("hasAuthority('CATALOGO_GESTIONAR')")
    @PostMapping
    public ResponseEntity<HabitatResponse> crear(@Valid @RequestBody HabitatRequest request) {
        HabitatResponse creado = habitatService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Actualizar un hábitat",
            description = "No cambia la bandera de estado: para dar de baja está el DELETE y "
                    + "para recuperar, PATCH /{id}/activar. Conservar el nombre actual es válido.")
    @ApiResponse(responseCode = "200", description = "Hábitat actualizado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe un hábitat con ese id")
    @ApiResponse(responseCode = "409",
            description = "Otro hábitat ya usa ese nombre. Si está activo: \"Ya existe un "
                    + "hábitat con el nombre 'X'.\" Si está desactivado: \"Ya existe el hábitat "
                    + "'X', pero está desactivado. Actívelo con PATCH /api/habitats/{id}/activar.\"")
    @PreAuthorize("hasAuthority('CATALOGO_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<HabitatResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody HabitatRequest request) {

        return ResponseEntity.ok(habitatService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un hábitat",
            description = "No borra la fila: marca el hábitat como inactivo. Se recupera con "
                    + "PATCH /{id}/activar.")
    @ApiResponse(responseCode = "204", description = "Hábitat desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un hábitat con ese id")
    @PreAuthorize("hasAuthority('CATALOGO_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        habitatService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activar un hábitat desactivado",
            description = "Revierte la baja lógica. Es idempotente: sobre un hábitat que ya "
                    + "está activo no cambia nada y devuelve 200 igualmente.")
    @ApiResponse(responseCode = "200", description = "Hábitat activo")
    @ApiResponse(responseCode = "404", description = "No existe un hábitat con ese id")
    @PreAuthorize("hasAuthority('CATALOGO_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<HabitatResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(habitatService.activar(id));
    }
}

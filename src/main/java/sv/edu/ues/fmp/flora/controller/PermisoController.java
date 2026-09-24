package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.PermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.service.PermisoService;

@Tag(name = "Permisos", description = "Catálogo de permisos granulares del sistema "
        + "(códigos que el backend usa para autorizar acciones)")
@RestController
@RequestMapping("/api/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoService permisoService;

    @Operation(summary = "Listar permisos",
            description = "Devuelve el catálogo completo. Con soloActivos=true omite "
                    + "los permisos dados de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<PermisoResponse>> listar(
            @Parameter(description = "Si es true, excluye los permisos desactivados")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<PermisoResponse> respuesta = soloActivos
                ? permisoService.listarActivos()
                : permisoService.listarTodos();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Obtener un permiso por su id")
    @ApiResponse(responseCode = "200", description = "Permiso encontrado")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "No existe un permiso con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<PermisoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(permisoService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un permiso",
            description = "El código es único en todo el catálogo, sin distinguir mayúsculas "
                    + "(aunque solo se aceptan mayúsculas, dígitos y guion bajo, p. ej. "
                    + "ESPECIE_VALIDAR). El nombre visible sí puede repetirse.")
    @ApiResponse(responseCode = "201", description = "Permiso creado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "409", description = "Ya existe un permiso con ese código")
    @PostMapping
    public ResponseEntity<PermisoResponse> crear(@Valid @RequestBody PermisoRequest request) {
        PermisoResponse creado = permisoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Actualizar un permiso",
            description = "No cambia la bandera de estado: para dar de baja está el DELETE, "
                    + "y para reactivar está el PATCH /activar.")
    @ApiResponse(responseCode = "200", description = "Permiso actualizado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe un permiso con ese id")
    @ApiResponse(responseCode = "409", description = "Ya existe otro permiso con ese código")
    @PutMapping("/{id}")
    public ResponseEntity<PermisoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PermisoRequest request) {

        return ResponseEntity.ok(permisoService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un permiso",
            description = "No borra la fila: rol_permiso la referencia por llave foránea y un "
                    + "DELETE físico rompería esas referencias. Los roles que ya lo tenían "
                    + "asignado conservan la fila en rol_permiso; validar el permiso en cada "
                    + "endpoint protegido debe considerar también que activo siga en true.")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "204", description = "Permiso desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un permiso con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        permisoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar un permiso dado de baja",
            description = "Vuelve a poner activo=true. Las asignaciones que tenía en "
                    + "rol_permiso nunca se borraron al desactivarlo, así que quedan vigentes "
                    + "de nuevo automáticamente para los roles que ya lo tenían.")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "200", description = "Permiso reactivado")
    @ApiResponse(responseCode = "404", description = "No existe un permiso con ese id")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<PermisoResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(permisoService.reactivar(id));
    }
}

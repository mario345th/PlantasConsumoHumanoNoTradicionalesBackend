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
import sv.edu.ues.fmp.flora.dto.request.RolRequest;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.service.RolService;

@Tag(name = "Roles", description = "Catálogo de roles internos (perfiles de acceso, p. ej. "
        + "ADMINISTRADOR, INVESTIGADOR) usados por el módulo de seguridad")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;

    @Operation(summary = "Listar roles",
            description = "Devuelve el catálogo completo. Con soloActivos=true omite "
                    + "los roles dados de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<RolResponse>> listar(
            @Parameter(description = "Si es true, excluye los roles desactivados")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<RolResponse> respuesta = soloActivos
                ? rolService.listarActivos()
                : rolService.listarTodos();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Obtener un rol por su id")
    @ApiResponse(responseCode = "200", description = "Rol encontrado")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "No existe un rol con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<RolResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(rolService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un rol",
            description = "El nombre es único en todo el catálogo, sin distinguir mayúsculas.")
    @ApiResponse(responseCode = "201", description = "Rol creado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "409", description = "Ya existe un rol con ese nombre")
    @PostMapping
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody RolRequest request) {
        RolResponse creado = rolService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Actualizar un rol",
            description = "No cambia la bandera de estado: para dar de baja está el DELETE, "
                    + "y para reactivar está el PATCH /activar.")
    @ApiResponse(responseCode = "200", description = "Rol actualizado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe un rol con ese id")
    @ApiResponse(responseCode = "409", description = "Ya existe otro rol con ese nombre")
    @PutMapping("/{id}")
    public ResponseEntity<RolResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RolRequest request) {

        return ResponseEntity.ok(rolService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un rol",
            description = "No borra la fila: usuario_rol y rol_permiso la referencian por "
                    + "llave foránea y un DELETE físico rompería esas referencias.")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "204", description = "Rol desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un rol con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        rolService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar un rol dado de baja",
            description = "Vuelve a poner activo=true. Las asignaciones que tenía en "
                    + "usuario_rol y rol_permiso nunca se borraron al desactivarlo, así que "
                    + "quedan vigentes de nuevo automáticamente.")
    @ApiResponse(responseCode = "400", description = "El id no es un valor positivo")
    @ApiResponse(responseCode = "200", description = "Rol reactivado")
    @ApiResponse(responseCode = "404", description = "No existe un rol con ese id")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<RolResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(rolService.reactivar(id));
    }
}

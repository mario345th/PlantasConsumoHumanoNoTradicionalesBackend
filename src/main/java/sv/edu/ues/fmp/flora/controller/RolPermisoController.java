package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.RolPermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolPermisoResponse;
import sv.edu.ues.fmp.flora.service.RolPermisoService;

@Tag(name = "Rol-Permiso", description = "Asignación de permisos a roles (tabla de unión "
        + "rol_permiso)")
@RestController
@RequestMapping("/api/rol-permisos")
@RequiredArgsConstructor
public class RolPermisoController {

    private final RolPermisoService rolPermisoService;

    @Operation(summary = "Listar los permisos de un rol",
            description = "Devuelve los permisos asignados al rol indicado a través de "
                    + "rol_permiso. Un rol sin permisos asignados devuelve una lista vacía, "
                    + "no un error.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400", description = "El id del rol no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "No existe un rol con ese id")
    @GetMapping("/rol/{idRol}")
    public ResponseEntity<List<PermisoResponse>> obtenerPermisosDeRol(@PathVariable Long idRol) {
        return ResponseEntity.ok(rolPermisoService.obtenerPermisosDeRol(idRol));
    }

    @Operation(summary = "Asignar un permiso a un rol",
            description = "Crea la fila en rol_permiso. La combinación idRol + idPermiso no "
                    + "puede repetirse.")
    @ApiResponse(responseCode = "201", description = "Permiso asignado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe el rol o el permiso indicado")
    @ApiResponse(responseCode = "409", description = "El rol ya tiene asignado ese permiso, o el rol/permiso está desactivado")
    @PostMapping
    public ResponseEntity<RolPermisoResponse> asignar(@Valid @RequestBody RolPermisoRequest request) {
        RolPermisoResponse creado = rolPermisoService.asignar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Quitar un permiso de un rol",
            description = "Borra físicamente la fila de rol_permiso (la relación en sí no "
                    + "tiene baja lógica: o el rol tiene el permiso, o no lo tiene).")
    @ApiResponse(responseCode = "204", description = "Permiso quitado del rol")
    @ApiResponse(responseCode = "400", description = "Algún id no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "El rol no tenía asignado ese permiso")
    @DeleteMapping("/rol/{idRol}/permiso/{idPermiso}")
    public ResponseEntity<Void> quitar(@PathVariable Long idRol, @PathVariable Long idPermiso) {
        rolPermisoService.quitar(idRol, idPermiso);
        return ResponseEntity.noContent().build();
    }
}

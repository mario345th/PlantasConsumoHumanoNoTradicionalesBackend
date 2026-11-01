package sv.edu.ues.fmp.flora.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sv.edu.ues.fmp.flora.dto.request.ParteBeneficioRequest;
import sv.edu.ues.fmp.flora.dto.request.ParteBeneficioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.ParteBeneficioResponse;
import sv.edu.ues.fmp.flora.service.ParteBeneficioService;

@RestController
@RequestMapping("/api/parte-beneficios")
@RequiredArgsConstructor
@Tag(name = "Parte beneficio", description = "Beneficios asociados a partes comestibles")
public class ParteBeneficioController {
    private final ParteBeneficioService parteBeneficioService;

    @Operation(summary = "Listar todos los registros de parte beneficio", description = "Devuelve los registros activos e inactivos de parte beneficio.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<ParteBeneficioResponse>> listarTodas() {
        return ResponseEntity.ok(parteBeneficioService.listarTodas());
    }

    @Operation(summary = "Listar los registros activos de parte beneficio",
            description = "Devuelve los registros de parte beneficio con activa=true, sin filtrar el estado de la parte, el beneficio o la fuente.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping("/activas")
    public ResponseEntity<List<ParteBeneficioResponse>> listarActivas() {
        return ResponseEntity.ok(parteBeneficioService.listarActivas());
    }

    @Operation(summary = "Obtener parte beneficio por ID",
            description = "Indica el ID de la parte comestible y el ID del beneficio. Ambos identifican el registro, que puede estar activo o inactivo.")
    @ApiResponse(responseCode = "200", description = "Parte beneficio encontrado")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "El registro de parte beneficio no existe")
    @GetMapping("/{idEspecieParte}/{idBeneficio}")
    public ResponseEntity<ParteBeneficioResponse> obtenerPorId(@PathVariable Long idEspecieParte,
                                                               @PathVariable Long idBeneficio) {
        return ResponseEntity.ok(parteBeneficioService.obtenerPorId(idEspecieParte, idBeneficio));
    }

    @Operation(summary = "Crear parte beneficio",
            description = "La especie, su parte comestible, el beneficio y la fuente deben existir y estar activos. Los IDs del JSON deben ser enteros sin comillas ni decimales. Fuente y observación "
                    + "son obligatorias. El registro se crea activo. No se permite repetir la misma parte y beneficio, aunque el registro anterior esté inactivo.")
    @ApiResponse(responseCode = "201", description = "Parte beneficio creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos obligatorios ausentes")
    @ApiResponse(responseCode = "404", description = "No existe un registro relacionado")
    @ApiResponse(responseCode = "409", description = "Combinación duplicada, relación inactiva o conflicto de integridad")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @PostMapping
    public ResponseEntity<ParteBeneficioResponse> crear(@Valid @RequestBody ParteBeneficioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parteBeneficioService.crear(request));
    }

    @Operation(summary = "Actualizar parte beneficio",
            description = "Permite editar la fuente, la observación y el estado. Fuente y observación son obligatorias. Los dos IDs de la URL no se modifican. "
                    + "Si omites activa o envías null, se conserva el estado actual. Para dejar el registro activo, "
                    + "la especie, su parte comestible, el beneficio y la fuente deben estar activos. idFuente debe ser un entero sin comillas ni decimales. También puedes editar un registro y mantenerlo inactivo.")
    @ApiResponse(responseCode = "200", description = "Parte beneficio actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "No existe el registro de parte beneficio o un registro relacionado")
    @ApiResponse(responseCode = "409", description = "Relación inactiva o conflicto de integridad")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @PutMapping("/{idEspecieParte}/{idBeneficio}")
    public ResponseEntity<ParteBeneficioResponse> actualizar(@PathVariable Long idEspecieParte,
                                                             @PathVariable Long idBeneficio, @Valid @RequestBody ParteBeneficioUpdateRequest request) {
        return ResponseEntity.ok(parteBeneficioService.actualizar(idEspecieParte, idBeneficio, request));
    }

    @Operation(summary = "Desactivar parte beneficio",
            description = "Cambia el estado a activa=false sin eliminar el registro de la base de datos. Puedes repetir la operación aunque ya esté inactivo.")
    @ApiResponse(responseCode = "204", description = "Parte beneficio desactivado")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "El registro de parte beneficio no existe")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @DeleteMapping("/{idEspecieParte}/{idBeneficio}")
    public ResponseEntity<Void> desactivar(@PathVariable Long idEspecieParte, @PathVariable Long idBeneficio) {
        parteBeneficioService.desactivar(idEspecieParte, idBeneficio);
        return ResponseEntity.noContent().build();
    }
}

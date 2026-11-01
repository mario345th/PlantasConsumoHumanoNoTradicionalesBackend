package sv.edu.ues.fmp.flora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.service.NombreComunService;


@Tag(name = "Nombres comunes", description = "Nombres vernáculos de una especie")
@RestController
@RequestMapping("/api/nombres-comunes")
@RequiredArgsConstructor
public class NombreComunController {

    private final NombreComunService nombreComunService;

    @Operation(summary = "Obtener un nombre común por su id")
    @GetMapping("/{id}")
    public ResponseEntity<NombreComunResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(nombreComunService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar un nombre común")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<NombreComunResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody NombreComunRequest request) {

        return ResponseEntity.ok(nombreComunService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un nombre común")
    @ApiResponse(responseCode = "204", description = "Nombre común desactivado")
    @ApiResponse(responseCode = "409",
            description = "Es el principal y la especie conserva otros nombres activos")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        nombreComunService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Se usa PATCH y no PUT porque solo cambia la bandera de estado, sin tocar
     * el resto del recurso.
     */
    @Operation(summary = "Reactivar un nombre común dado de baja")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<NombreComunResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(nombreComunService.activar(id));
    }

    /**
     * Promocion parcial: evita tener que reenviar el recurso completo por PUT
     * solo para cambiar cual es el nombre principal de la especie.
     */
    @Operation(summary = "Marcar el nombre como principal de su especie")
    @ApiResponse(responseCode = "409", description = "El nombre está desactivado")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PatchMapping("/{id}/marcar-principal")
    public ResponseEntity<NombreComunResponse> marcarComoPrincipal(@PathVariable Long id) {
        return ResponseEntity.ok(nombreComunService.marcarComoPrincipal(id));
    }
}

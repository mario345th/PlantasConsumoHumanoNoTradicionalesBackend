package sv.edu.ues.fmp.flora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.ImagenEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.ImagenEspecieResponse;
import sv.edu.ues.fmp.flora.service.ImagenEspecieService;

/** Expone consulta, edición, baja lógica, activación y promoción de una imagen existente. */
@Tag(name = "Imágenes de especie", description = "Imágenes por URL y selección de principal")
@RestController
@RequestMapping("/api/imagenes-especie")
@RequiredArgsConstructor
public class ImagenEspecieController {
    private final ImagenEspecieService imagenEspecieService;

    /** Consulta una imagen por su identificador. */
    @Operation(summary = "Obtener una imagen")
    @ApiResponse(responseCode = "200", description = "Imagen encontrada")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @GetMapping("/{id}")
    public ResponseEntity<ImagenEspecieResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(imagenEspecieService.obtenerPorId(id));
    }

    /** Reemplaza los datos editables sin cambiar la especie ni el estado. */
    @Operation(summary = "Actualizar una imagen",
            description = "Reemplaza los campos editables. Los opcionales omitidos quedan en null y "
                    + "esPrincipal omitido o nulo equivale a false. La especie no cambia; la parte "
                    + "comestible opcional debe pertenecer a esa misma especie.")
    @ApiResponse(responseCode = "200", description = "Imagen actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "La imagen o la parte comestible no existe")
    @ApiResponse(responseCode = "409", description = "Estado inválido, parte ajena o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<ImagenEspecieResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody ImagenEspecieRequest request) {
        return ResponseEntity.ok(imagenEspecieService.actualizar(id, request));
    }

    /** Permite repetir la baja; si era principal pierde dicha condición. */
    @Operation(summary = "Desactivar una imagen",
            description = "Baja lógica idempotente; no elimina la fila. Si era principal, pierde dicha condición.")
    @ApiResponse(responseCode = "204", description = "Imagen desactivada")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        imagenEspecieService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /** Reactiva la imagen como no-principal. */
    @Operation(summary = "Activar una imagen",
            description = "Idempotente. La imagen se reactiva como no-principal.")
    @ApiResponse(responseCode = "200", description = "Imagen activa")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen o la parte comestible no existe")
    @ApiResponse(responseCode = "409", description = "Especie o parte desactivada, parte ajena o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<ImagenEspecieResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(imagenEspecieService.activar(id));
    }

    /** Promueve una imagen activa; repetir la operación conserva la principal. */
    @Operation(summary = "Marcar una imagen como principal",
            description = "Idempotente. La imagen debe estar activa y desplaza a la principal anterior.")
    @ApiResponse(responseCode = "200", description = "Imagen principal")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @ApiResponse(responseCode = "409", description = "Imagen o especie desactivada, o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/marcar-principal")
    public ResponseEntity<ImagenEspecieResponse> marcarComoPrincipal(@PathVariable Long id) {
        return ResponseEntity.ok(imagenEspecieService.marcarComoPrincipal(id));
    }
}

package sv.edu.ues.fmp.flora.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import sv.edu.ues.fmp.flora.dto.request.VideoEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoEspecieResponse;
import sv.edu.ues.fmp.flora.service.VideoEspecieService;

@Tag(name = "Videos de especies", description = "Videos asociados a especies")
@RestController
@RequestMapping("/api/videos-especie")
@RequiredArgsConstructor
public class VideoEspecieController {

    private final VideoEspecieService videoEspecieService;

    @Operation(summary = "Obtener un video por su id")
    @ApiResponse(responseCode = "200", description = "Video encontrado")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @GetMapping("/{id}")
    public ResponseEntity<VideoEspecieResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(videoEspecieService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar los datos de un video")
    @ApiResponse(responseCode = "200", description = "Video actualizado")
    @ApiResponse(responseCode = "400", description = "Los campos no son válidos")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @ApiResponse(responseCode = "409", description = "La especie o parte está desactivada")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<VideoEspecieResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody VideoEspecieRequest request) {
        return ResponseEntity.ok(videoEspecieService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un video")
    @ApiResponse(responseCode = "204", description = "Video desactivado")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        videoEspecieService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar un video")
    @ApiResponse(responseCode = "200", description = "Video activo")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @ApiResponse(responseCode = "409", description = "La especie o parte está desactivada")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<VideoEspecieResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(videoEspecieService.activar(id));
    }
}

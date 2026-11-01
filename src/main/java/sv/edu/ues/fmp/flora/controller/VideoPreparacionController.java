package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoPreparacionResponse;
import sv.edu.ues.fmp.flora.service.VideoPreparacionService;

@Tag(name = "Videos de preparaciones", description = "Gestión de videos asociados a preparaciones de consumo")
@RestController
@RequestMapping("/api/videos-preparacion")
@RequiredArgsConstructor
public class VideoPreparacionController {

    private final VideoPreparacionService videoPreparacionService;

    @Operation(summary = "Obtener un video de preparación por su ID")
    @ApiResponse(responseCode = "200", description = "Video encontrado")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @GetMapping("/{id}")
    public ResponseEntity<VideoPreparacionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(videoPreparacionService.obtenerPorId(id));
    }

    @Operation(summary = "Listar videos por ID de preparación de consumo")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404", description = "La preparación no existe")
    @GetMapping("/preparacion/{idPreparacion}")
    public ResponseEntity<List<VideoPreparacionResponse>> listarPorPreparacion(
            @PathVariable Long idPreparacion,
            @Parameter(description = "Si es true, excluye videos inactivos")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(videoPreparacionService.listarPorPreparacion(idPreparacion, soloActivos));
    }

    @Operation(summary = "Actualizar los datos de un video de preparación")
    @ApiResponse(responseCode = "200", description = "Video actualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<VideoPreparacionResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody VideoPreparacionRequest request) {
        return ResponseEntity.ok(videoPreparacionService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un video de preparación")
    @ApiResponse(responseCode = "204", description = "Video desactivado")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        videoPreparacionService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar un video de preparación")
    @ApiResponse(responseCode = "200", description = "Video reactivado")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "El video no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<VideoPreparacionResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(videoPreparacionService.activar(id));
    }
}


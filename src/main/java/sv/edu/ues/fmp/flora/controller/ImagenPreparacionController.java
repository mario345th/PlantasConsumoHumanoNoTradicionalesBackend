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
import sv.edu.ues.fmp.flora.dto.request.ImagenPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.ImagenPreparacionResponse;
import sv.edu.ues.fmp.flora.service.ImagenPreparacionService;

@Tag(name = "Imágenes de preparaciones", description = "Gestión de imágenes por URL y selección de imagen principal por preparación")
@RestController
@RequestMapping("/api/imagenes-preparacion")
@RequiredArgsConstructor
public class ImagenPreparacionController {

    private final ImagenPreparacionService imagenPreparacionService;

    @Operation(summary = "Obtener una imagen de preparación por ID")
    @ApiResponse(responseCode = "200", description = "Imagen encontrada")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @GetMapping("/{id}")
    public ResponseEntity<ImagenPreparacionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(imagenPreparacionService.obtenerPorId(id));
    }

    @Operation(summary = "Listar imágenes por ID de preparación de consumo")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404", description = "La preparación no existe")
    @GetMapping("/preparacion/{idPreparacion}")
    public ResponseEntity<List<ImagenPreparacionResponse>> listarPorPreparacion(
            @PathVariable Long idPreparacion,
            @Parameter(description = "Si es true, excluye imágenes inactivas")
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {
        return ResponseEntity.ok(imagenPreparacionService.listarPorPreparacion(idPreparacion, soloActivas));
    }

    @Operation(summary = "Actualizar una imagen de preparación",
            description = "Reemplaza los campos editables. Si esPrincipal cambia a true, desplaza a la principal actual.")
    @ApiResponse(responseCode = "200", description = "Imagen actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<ImagenPreparacionResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ImagenPreparacionRequest request) {
        return ResponseEntity.ok(imagenPreparacionService.actualizar(id, request));
    }

    @Operation(summary = "Desactivar una imagen de preparación",
            description = "Baja lógica idempotente (activa=false, esPrincipal=false). No elimina físicamente el registro.")
    @ApiResponse(responseCode = "204", description = "Imagen desactivada")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        imagenPreparacionService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar una imagen de preparación",
            description = "Idempotente. La imagen se reactiva como no-principal.")
    @ApiResponse(responseCode = "200", description = "Imagen activada")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<ImagenPreparacionResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(imagenPreparacionService.activar(id));
    }

    @Operation(summary = "Marcar una imagen como principal de su preparación",
            description = "Idempotente. La imagen debe estar activa y desplaza a la imagen principal anterior.")
    @ApiResponse(responseCode = "200", description = "Imagen marcada como principal")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "404", description = "La imagen no existe")
    @ApiResponse(responseCode = "409", description = "Imagen o preparación desactivada, o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PatchMapping("/{id}/marcar-principal")
    public ResponseEntity<ImagenPreparacionResponse> marcarComoPrincipal(@PathVariable Long id) {
        return ResponseEntity.ok(imagenPreparacionService.marcarComoPrincipal(id));
    }
}


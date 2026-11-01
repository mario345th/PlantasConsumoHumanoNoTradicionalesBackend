package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/preparaciones-consumo/{idPreparacion}/videos")
@RequiredArgsConstructor
public class PreparacionVideoController {

    private final VideoPreparacionService videoPreparacionService;

    @Operation(summary = "Listar videos de una preparación de consumo")
    @ApiResponse(responseCode = "200", description = "Listado de videos obtenido")
    @ApiResponse(responseCode = "400", description = "Identificador o parámetros inválidos")
    @ApiResponse(responseCode = "404", description = "La preparación de consumo no existe")
    @GetMapping
    public ResponseEntity<List<VideoPreparacionResponse>> listar(
            @PathVariable Long idPreparacion,
            @Parameter(description = "Si es true, excluye videos inactivos")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(videoPreparacionService.listarPorPreparacion(idPreparacion, soloActivos));
    }

    @Operation(summary = "Registrar un video para una preparación de consumo",
            description = "Recibe una URL HTTP(S) de video, no archivos binarios.")
    @ApiResponse(responseCode = "201", description = "Video registrado exitosamente")
    @ApiResponse(responseCode = "400", description = "Campos del video inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "La preparación de consumo no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PostMapping
    public ResponseEntity<VideoPreparacionResponse> crear(
            @PathVariable Long idPreparacion,
            @Valid @RequestBody VideoPreparacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(videoPreparacionService.crear(idPreparacion, request));
    }
}


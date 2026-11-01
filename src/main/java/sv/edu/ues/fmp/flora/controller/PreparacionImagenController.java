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
import sv.edu.ues.fmp.flora.dto.request.ImagenPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.ImagenPreparacionResponse;
import sv.edu.ues.fmp.flora.service.ImagenPreparacionService;

@Tag(name = "Imágenes de preparaciones", description = "Gestión de imágenes por URL y selección de imagen principal por preparación")
@RestController
@RequestMapping("/api/preparaciones-consumo/{idPreparacion}/imagenes")
@RequiredArgsConstructor
public class PreparacionImagenController {

    private final ImagenPreparacionService imagenPreparacionService;

    @Operation(summary = "Listar imágenes de una preparación de consumo",
            description = "Con soloActivas=true excluye las imágenes dadas de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado de imágenes obtenido")
    @ApiResponse(responseCode = "400", description = "Identificador o parámetros inválidos")
    @ApiResponse(responseCode = "404", description = "La preparación de consumo no existe")
    @GetMapping
    public ResponseEntity<List<ImagenPreparacionResponse>> listar(
            @PathVariable Long idPreparacion,
            @Parameter(description = "Si es true, excluye imágenes inactivas")
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {
        return ResponseEntity.ok(imagenPreparacionService.listarPorPreparacion(idPreparacion, soloActivas));
    }

    @Operation(summary = "Registrar una imagen para una preparación de consumo",
            description = "Recibe una URL HTTP(S), no archivos binarios. Si se marca como principal, desplaza a la principal anterior.")
    @ApiResponse(responseCode = "201", description = "Imagen registrada exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "La preparación de consumo no existe")
    @ApiResponse(responseCode = "409", description = "La preparación está desactivada o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PostMapping
    public ResponseEntity<ImagenPreparacionResponse> crear(
            @PathVariable Long idPreparacion,
            @Valid @RequestBody ImagenPreparacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imagenPreparacionService.crear(idPreparacion, request));
    }
}


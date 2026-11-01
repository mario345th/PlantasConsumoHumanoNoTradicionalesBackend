package sv.edu.ues.fmp.flora.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
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

/** Expone el listado y la creación de imágenes dentro de una especie. */
@Tag(name = "Imágenes de especie", description = "Imágenes por URL y selección de principal")
@RestController
@RequestMapping("/api/especies/{idEspecie}/imagenes")
@RequiredArgsConstructor
public class EspecieImagenController {
    private final ImagenEspecieService imagenEspecieService;

    /** Delega la consulta del historial o de las imágenes activas. */
    @Operation(summary = "Listar imágenes de una especie",
            description = "Con soloActivas=true excluye las imágenes dadas de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400", description = "Identificador o filtro inválido")
    @ApiResponse(responseCode = "404", description = "La especie no existe")
    @GetMapping
    public ResponseEntity<List<ImagenEspecieResponse>> listar(
            @PathVariable Long idEspecie,
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {
        return ResponseEntity.ok(imagenEspecieService.listarPorEspecie(idEspecie, soloActivas));
    }

    /** Registra una URL y devuelve la imagen con la fecha asignada por PostgreSQL. */
    @Operation(summary = "Registrar una imagen de especie",
            description = "Recibe una URL HTTP(S), no archivos. Si es principal desplaza a la anterior.")
    @ApiResponse(responseCode = "201", description = "Imagen registrada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o URL incorrecta")
    @ApiResponse(responseCode = "404", description = "La especie o la parte comestible no existe")
    @ApiResponse(responseCode = "409", description = "Especie o parte desactivada, parte ajena o conflicto de integridad")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PostMapping
    public ResponseEntity<ImagenEspecieResponse> crear(@PathVariable Long idEspecie,
            @Valid @RequestBody ImagenEspecieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imagenEspecieService.crear(idEspecie, request));
    }
}

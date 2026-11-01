package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import sv.edu.ues.fmp.flora.dto.request.VideoEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoEspecieResponse;
import sv.edu.ues.fmp.flora.service.VideoEspecieService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

@Tag(name = "Videos de especies", description = "Videos asociados a especies")
@RestController
@RequestMapping("/api/especies/{idEspecie}/videos")
@RequiredArgsConstructor
public class EspecieVideoController {

    private final VideoEspecieService videoEspecieService;

    /**
     * Decide si quien llama puede ver el contenido de esta especie: publicada
     * para cualquiera, sin publicar solo con ESPECIE_CONSULTAR, y 404 en el
     * resto para no delatar los borradores.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Operation(summary = "Listar videos de una especie")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404", description = "La especie no existe")
    @GetMapping
    public ResponseEntity<List<VideoEspecieResponse>> listar(
            @PathVariable Long idEspecie,
            @Parameter(description = "Si es true, excluye videos inactivos")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {
        visibilidadDeEspecie.verificarQuePuedeVer(idEspecie);

        return ResponseEntity.ok(videoEspecieService.listarPorEspecie(idEspecie, soloActivos));
    }

    @Operation(summary = "Registrar un video para una especie")
    @ApiResponse(responseCode = "201", description = "Video registrado")
    @ApiResponse(responseCode = "400", description = "Los campos del video no son válidos")
    @ApiResponse(responseCode = "404", description = "La especie o parte no existe")
    @ApiResponse(responseCode = "409", description = "La especie o parte está desactivada")
    @PreAuthorize("hasAuthority('MULTIMEDIA_GESTIONAR')")
    @PostMapping
    public ResponseEntity<VideoEspecieResponse> crear(
            @PathVariable Long idEspecie,
            @Valid @RequestBody VideoEspecieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(videoEspecieService.crear(idEspecie, request));
    }
}

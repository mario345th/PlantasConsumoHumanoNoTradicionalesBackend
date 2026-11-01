package sv.edu.ues.fmp.flora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sv.edu.ues.fmp.flora.dto.request.UbicacionEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.UbicacionEspecieResponse;
import sv.edu.ues.fmp.flora.service.UbicacionEspecieService;

import java.util.List;

@RestController
@RequestMapping("/api/ubicaciones")
@RequiredArgsConstructor
@Tag(name = "Ubicaciones de Especies", description = "Endpoints para gestionar los avistamientos y distribución geográfica (Distritos) de las especies")
public class UbicacionEspecieController {

    private final UbicacionEspecieService ubicacionEspecieService;

    @Operation(summary = "Registrar nueva ubicación", description = "Guarda una nueva ubicación o avistamiento para una especie vinculándola con un distrito")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ubicación registrada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de petición inválidos o incompletos"),
            @ApiResponse(responseCode = "404", description = "La Especie o el Distrito no fueron encontrados en la base de datos"),
            @ApiResponse(responseCode = "409", description = "La especie está desactivada o conflicto de coordenadas")
    })
    @PreAuthorize("hasAuthority('UBICACION_GESTIONAR')")
    @PostMapping
    public ResponseEntity<UbicacionEspecieResponse> crear(@Valid @RequestBody UbicacionEspecieRequest request) {
        UbicacionEspecieResponse response = ubicacionEspecieService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Obtener ubicaciones por especie", description = "Devuelve una lista de todas las ubicaciones activas asociadas a una especie específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de ubicaciones recuperada exitosamente"),
            @ApiResponse(responseCode = "404", description = "No existe una especie con ese id")
    })
    @GetMapping("/especie/{idEspecie}")
    public ResponseEntity<List<UbicacionEspecieResponse>> obtenerPorEspecie(@PathVariable Long idEspecie) {
        List<UbicacionEspecieResponse> ubicaciones = ubicacionEspecieService.obtenerUbicacionesPorEspecie(idEspecie);
        return ResponseEntity.ok(ubicaciones);
    }

    @Operation(summary = "Actualizar una ubicación", description = "Actualiza los datos de un avistamiento existente usando su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ubicación actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de actualización inválidos"),
            @ApiResponse(responseCode = "404", description = "La Ubicación, Especie o Distrito no fueron encontrados"),
            @ApiResponse(responseCode = "409", description = "La especie está desactivada")
    })
    @PreAuthorize("hasAuthority('UBICACION_GESTIONAR')")
    @PutMapping("/{idUbicacion}")
    public ResponseEntity<UbicacionEspecieResponse> actualizarUbicacion(
            @PathVariable Long idUbicacion,
            @Valid @RequestBody UbicacionEspecieRequest request) {
        UbicacionEspecieResponse response = ubicacionEspecieService.actualizarUbicacion(idUbicacion, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Desactivar ubicación (Borrado lógico)", description = "Desactiva la ubicación para no mostrarla, conservando el historial según requerimientos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Ubicación desactivada exitosamente (Sin contenido de respuesta)"),
            @ApiResponse(responseCode = "404", description = "Ubicación no encontrada")
    })
    @PreAuthorize("hasAuthority('UBICACION_GESTIONAR')")
    @DeleteMapping("/{idUbicacion}")
    public ResponseEntity<Void> desactivarUbicacion(@PathVariable Long idUbicacion) {
        ubicacionEspecieService.desactivarUbicacion(idUbicacion);
        return ResponseEntity.noContent().build();
    }
}

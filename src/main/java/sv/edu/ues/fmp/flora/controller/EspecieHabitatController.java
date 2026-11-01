package sv.edu.ues.fmp.flora.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sv.edu.ues.fmp.flora.dto.request.EspecieHabitatRequest;
import sv.edu.ues.fmp.flora.dto.request.EspecieHabitatActualizarRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieHabitatResponse;
import sv.edu.ues.fmp.flora.service.EspecieHabitatService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

/** Expone los hábitats como recursos de una especie y delega sus reglas al servicio. */
@Tag(name = "Especie-Hábitat", description = "Hábitats asociados a cada especie")
@RestController
@RequestMapping("/api/especies/{idEspecie}/habitats")
@RequiredArgsConstructor
public class EspecieHabitatController {

    private final EspecieHabitatService especieHabitatService;

    /**
     * Decide si quien llama puede ver el contenido de esta especie: publicada
     * para cualquiera, sin publicar solo con ESPECIE_CONSULTAR, y 404 en el
     * resto para no delatar los borradores.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    /** Devuelve los hábitats de la especie ordenados por nombre, o una lista vacía. */
    @Operation(summary = "Consultar los hábitats de una especie")
    @ApiResponse(responseCode = "200", description = "Relaciones ordenadas por nombre del hábitat")
    @ApiResponse(responseCode = "400", description = "ID de especie inválido")
    @ApiResponse(responseCode = "404", description = "La especie no existe")
    @GetMapping
    public ResponseEntity<List<EspecieHabitatResponse>> listarPorEspecie(@PathVariable Long idEspecie) {
        visibilidadDeEspecie.verificarQuePuedeVer(idEspecie);

        return ResponseEntity.ok(especieHabitatService.listarPorEspecie(idEspecie));
    }

    /** Valida el cuerpo y crea una asociación con la especie indicada en la URL. */
    @Operation(summary = "Asociar un hábitat a una especie",
            description = "La especie y el hábitat deben estar activos. La especie se toma de la URL.")
    @ApiResponse(responseCode = "201", description = "Relación creada")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida o falta idHabitat")
    @ApiResponse(responseCode = "404", description = "La especie o el hábitat no existe")
    @ApiResponse(responseCode = "409", description = "Relación duplicada, padre desactivado o conflicto de integridad")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PostMapping
    public ResponseEntity<EspecieHabitatResponse> crear(
            @PathVariable Long idEspecie, @Valid @RequestBody EspecieHabitatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especieHabitatService.crear(idEspecie, request));
    }

    /** Modifica solo la observación; ambos componentes de la clave se reciben en la URL. */
    @Operation(summary = "Actualizar la observación",
            description = "El cuerpo solo contiene observacion. El hábitat no se puede cambiar porque "
                    + "forma parte de la clave primaria; para cambiarlo hay que eliminar la relación y crear otra.")
    @ApiResponse(responseCode = "200", description = "Observación actualizada")
    @ApiResponse(responseCode = "400", description = "Solicitud o IDs inválidos")
    @ApiResponse(responseCode = "404", description = "La relación no existe")
    @ApiResponse(responseCode = "409", description = "Conflicto de integridad")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PutMapping("/{idHabitat}")
    public ResponseEntity<EspecieHabitatResponse> actualizar(
            @PathVariable Long idEspecie, @PathVariable Long idHabitat,
            @Valid @RequestBody EspecieHabitatActualizarRequest request) {
        return ResponseEntity.ok(especieHabitatService.actualizar(idEspecie, idHabitat, request));
    }

    /** Elimina únicamente la asociación y conserva las dos entidades padre. */
    @Operation(summary = "Eliminar la relación con un hábitat")
    @ApiResponse(responseCode = "204", description = "Relación eliminada")
    @ApiResponse(responseCode = "400", description = "IDs inválidos")
    @ApiResponse(responseCode = "404", description = "La relación no existe")
    @ApiResponse(responseCode = "409", description = "Conflicto de integridad")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @DeleteMapping("/{idHabitat}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idEspecie, @PathVariable Long idHabitat) {
        especieHabitatService.eliminar(idEspecie, idHabitat);
        return ResponseEntity.noContent().build();
    }
}

package sv.edu.ues.fmp.flora.controller;

import java.util.List;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieFuenteRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieFuenteResponse;
import sv.edu.ues.fmp.flora.service.EspecieFuenteService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

@Tag(name = "Especies-Fuentes", description = "Gestión de las referencias bibliográficas y documentales de cada especie")
@RestController
@RequestMapping("/api/especies/{idEspecie}/fuentes")
@RequiredArgsConstructor
public class EspecieFuenteController {

    private final EspecieFuenteService especieFuenteService;

    /**
     * Decide si quien llama puede ver el contenido de esta especie: publicada
     * para cualquiera, sin publicar solo con ESPECIE_CONSULTAR, y 404 en el
     * resto para no delatar los borradores.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Operation(summary = "Listar todas las fuentes vinculadas a una especie")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404", description = "No existe la especie")
    @GetMapping
    public ResponseEntity<List<EspecieFuenteResponse>> listarPorEspecie(@PathVariable Long idEspecie) {
        visibilidadDeEspecie.verificarQuePuedeVer(idEspecie);

        return ResponseEntity.ok(especieFuenteService.listarPorEspecie(idEspecie));
    }

    @Operation(summary = "Vincular una fuente a una especie")
    @ApiResponse(responseCode = "201", description = "Fuente vinculada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos en el request")
    @ApiResponse(responseCode = "404", description = "No existe la especie o la fuente indicada, o alguna de ellas está inactiva")
    @ApiResponse(responseCode = "409", description = "La fuente ya está vinculada a esta especie")
    @PreAuthorize("hasAuthority('FUENTE_GESTIONAR')")
    @PostMapping
    public ResponseEntity<EspecieFuenteResponse> registrar(
            @PathVariable Long idEspecie,
            @Valid @RequestBody EspecieFuenteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especieFuenteService.registrar(idEspecie, request));
    }

    @Operation(summary = "Actualizar la observación de una fuente ya vinculada")
    @ApiResponse(responseCode = "200", description = "Observación actualizada")
    @ApiResponse(responseCode = "404", description = "La relación no existe")
    @PreAuthorize("hasAuthority('FUENTE_GESTIONAR')")
    @PutMapping("/{idFuente}")
    public ResponseEntity<EspecieFuenteResponse> actualizar(
            @PathVariable Long idEspecie,
            @PathVariable Long idFuente,
            @Valid @RequestBody EspecieFuenteRequest request) {
        return ResponseEntity.ok(especieFuenteService.actualizar(idEspecie, idFuente, request));
    }

    @Operation(summary = "Desvincular una fuente de una especie (Eliminación física de la relación)")
    @ApiResponse(responseCode = "204", description = "Vínculo eliminado")
    @ApiResponse(responseCode = "404", description = "La relación no existe")
    @PreAuthorize("hasAuthority('FUENTE_GESTIONAR')")
    @DeleteMapping("/{idFuente}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idEspecie, @PathVariable Long idFuente) {
        especieFuenteService.eliminar(idEspecie, idFuente);
        return ResponseEntity.noContent().build();
    }
}

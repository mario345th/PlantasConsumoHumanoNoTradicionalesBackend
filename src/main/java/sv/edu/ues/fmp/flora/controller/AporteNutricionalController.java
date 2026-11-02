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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.AporteNutricionalRequest;
import sv.edu.ues.fmp.flora.dto.response.AporteNutricionalResponse;
import sv.edu.ues.fmp.flora.service.AporteNutricionalService;

@RestController
@RequestMapping("/api/aportes-nutricionales")
@RequiredArgsConstructor
@Tag(name = "Aportes Nutricionales", description = "Gestión de los nutrientes aportados por una parte específica de la planta")
public class AporteNutricionalController {

    private final AporteNutricionalService aporteService;

    @Operation(summary = "Listar aportes por especie y parte", description = "Obtiene todos los nutrientes registrados para una parte específica de una planta.")
    @GetMapping("/especie-parte/{idEspecieParte}")
    public ResponseEntity<List<AporteNutricionalResponse>> listarPorEspecieParte(@PathVariable Long idEspecieParte) {
        return ResponseEntity.ok(aporteService.listarPorEspecieParte(idEspecieParte));
    }

    /**
     * Listado administrativo: el id de la URL es el del nutriente, no el de una
     * ficha, asi que la consulta cruza todas las especies y sin permiso
     * mostraria las partes de los borradores. El listado por especie-parte, que
     * si entra por el padre, sigue abierto.
     */
    @Operation(summary = "Listar aportes por nutriente",
            description = "Requiere el permiso ESPECIE_CONSULTAR: cruza todas las especies, incluidas las que no estan publicadas. "
                    + "Obtiene todas las plantas/partes que aportan un nutriente específico.")
    @PreAuthorize("hasAuthority('ESPECIE_CONSULTAR')")
    @GetMapping("/nutriente/{idNutriente}")
    public ResponseEntity<List<AporteNutricionalResponse>> listarPorNutriente(@PathVariable Long idNutriente) {
        return ResponseEntity.ok(aporteService.listarPorNutriente(idNutriente));
    }

    @Operation(summary = "Obtener un aporte nutricional por ID")
    @GetMapping("/{id}")
    public ResponseEntity<AporteNutricionalResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(aporteService.obtenerPorId(id));
    }

    @Operation(summary = "Registrar un nuevo aporte nutricional")
    @PreAuthorize("hasAuthority('NUTRICION_GESTIONAR')")
    @PostMapping
    public ResponseEntity<AporteNutricionalResponse> crear(
            @Valid @RequestBody AporteNutricionalRequest request) {
        AporteNutricionalResponse creada = aporteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }


    @Operation(summary = "Actualizar un aporte nutricional existente")
    @PreAuthorize("hasAuthority('NUTRICION_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<AporteNutricionalResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody AporteNutricionalRequest request) {
        return ResponseEntity.ok(aporteService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar físicamente un aporte nutricional")
    @PreAuthorize("hasAuthority('NUTRICION_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        aporteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

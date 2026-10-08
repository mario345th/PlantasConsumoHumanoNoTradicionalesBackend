package sv.edu.ues.fmp.flora.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoRequest;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.PreparacionConsumoResponse;
import sv.edu.ues.fmp.flora.service.PreparacionConsumoService;

@RestController
@RequestMapping("/api/preparaciones-consumo")
@RequiredArgsConstructor
@Tag(name = "Preparaciones de consumo", description = "Preparaciones de las partes comestibles")
@ApiResponse(responseCode = "400", description = "Datos, identificador o parámetros inválidos")
@ApiResponse(responseCode = "404", description = "No existe la preparación o la parte comestible")
@ApiResponse(responseCode = "409", description = "Nombre duplicado en la parte, parte inactiva o conflicto de integridad")
public class PreparacionConsumoController {
    private final PreparacionConsumoService service;

    @GetMapping
    @Operation(summary = "Listar preparaciones de consumo",
            description = "Incluye activas e inactivas. soloActivas=true filtra el estado de la preparación. Ordenadas por orden de presentación e ID.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    public ResponseEntity<List<PreparacionConsumoResponse>> listar(
            @RequestParam(defaultValue = "false") boolean soloActivas) {
        return ResponseEntity.ok(service.listar(soloActivas));
    }

    @GetMapping("/parte/{idEspecieParte}")
    @Operation(summary = "Listar preparaciones por parte comestible",
            description = "soloActivas=true devuelve solo preparaciones activas. Si la parte existe sin preparaciones, devuelve una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    public ResponseEntity<List<PreparacionConsumoResponse>> listarPorParte(
            @PathVariable Long idEspecieParte,
            @RequestParam(defaultValue = "false") boolean soloActivas) {
        return ResponseEntity.ok(service.listarPorParte(idEspecieParte, soloActivas));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener preparación de consumo por ID",
            description = "Permite consultar preparaciones activas e inactivas.")
    @ApiResponse(responseCode = "200", description = "Preparación encontrada")
    public ResponseEntity<PreparacionConsumoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear preparación de consumo",
            description = "Parte comestible existente y activa; nombre, descripción, tipo de consumo y procedimiento obligatorios. "
                    + "No admite nombres repetidos en la misma parte, sin distinguir mayúsculas y aunque estén inactivos. "
                    + "Se crea activa. requiereCoccion omitido o null vale false; ordenPresentacion omitido o null vale 1.")
    @ApiResponse(responseCode = "201", description = "Preparación creada")
    public ResponseEntity<PreparacionConsumoResponse> crear(
            @Valid @RequestBody PreparacionConsumoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar preparación de consumo",
            description = "Parte, nombre, descripción, tipo de consumo y procedimiento son obligatorios. Permite cambiar la parte. "
                    + "activa, requiereCoccion y ordenPresentacion omitidos o null conservan sus valores. "
                    + "Los demás campos opcionales omitidos o null se limpian. "
                    + "Si quedará activa, la parte debe estar activa. Permite editar una preparación que seguirá inactiva.")
    @ApiResponse(responseCode = "200", description = "Preparación actualizada")
    public ResponseEntity<PreparacionConsumoResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody PreparacionConsumoUpdateRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar preparación de consumo",
            description = "Establece activa=false sin borrar el registro. Puede repetirse aunque ya esté inactivo. "
                    + "No modifica imágenes, videos ni la parte comestible.")
    @ApiResponse(responseCode = "204", description = "Preparación desactivada")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}

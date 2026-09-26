package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.FuenteRequest;
import sv.edu.ues.fmp.flora.dto.response.FuenteResponse;
import sv.edu.ues.fmp.flora.service.FuenteService;


@Tag(name = "Fuentes", description = "Fuentes bibliográficas o de conocimiento citadas por el sistema")
@RestController
@RequestMapping("/api/fuentes")
@RequiredArgsConstructor
public class FuenteController {
    private final FuenteService fuenteService;

    @Operation(summary = "Listar fuentes, opcionalmente solo las activas")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<FuenteResponse>> listar(
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {

        List<FuenteResponse> respuesta = soloActivas
                ? fuenteService.listarActivas()
                : fuenteService.listarTodas();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Obtener una fuente por su id")
    @ApiResponse(responseCode = "404", description = "No existe una fuente con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<FuenteResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(fuenteService.obtenerPorId(id));
    }

    @Operation(summary = "Registrar una nueva fuente")
    @ApiResponse(responseCode = "201", description = "Fuente creada")
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos (título vacío, tipo de fuente inexistente, "
                    + "o SITIO_WEB sin una url navegable)")
    @ApiResponse(responseCode = "409", description = "Ya existe una fuente con ese título")
    @PostMapping
    public ResponseEntity<FuenteResponse> crear(
            @Valid @RequestBody FuenteRequest request) {

        FuenteResponse creada = fuenteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Actualizar una fuente existente")
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos (título vacío, tipo de fuente inexistente, "
                    + "o SITIO_WEB sin una url navegable)")
    @ApiResponse(responseCode = "404", description = "No existe una fuente con ese id")
    @ApiResponse(responseCode = "409", description = "Ya existe otra fuente con ese título")
    @PutMapping("/{id}")
    public ResponseEntity<FuenteResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody FuenteRequest request) {

        return ResponseEntity.ok(fuenteService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica una fuente")
    @ApiResponse(responseCode = "204", description = "Fuente desactivada")
    @ApiResponse(responseCode = "404", description = "No existe una fuente con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        fuenteService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
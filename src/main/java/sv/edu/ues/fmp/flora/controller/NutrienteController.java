package sv.edu.ues.fmp.flora.controller;

import java.util.List;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.NutrienteRequest;
import sv.edu.ues.fmp.flora.dto.response.NutrienteResponse;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.service.NutrienteService;

@RestController
@RequestMapping("/api/nutrientes")
@RequiredArgsConstructor
@Tag(name = "Nutrientes", description = "Endpoints para la gestión del catálogo de Nutrientes")
public class NutrienteController {

    private final NutrienteService nutrienteService;

    @Operation(summary = "Listar nutrientes", description = "Obtiene todos los nutrientes. Permite filtrar solo los activos.")
    @GetMapping
    public ResponseEntity<List<NutrienteResponse>> listar(
            @Parameter(description = "Si es true, devuelve solo los nutrientes activos")
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {

        List<NutrienteResponse> respuesta = soloActivas
                ? nutrienteService.listarActivas()
                : nutrienteService.listarTodas();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Obtener nutriente por ID")
    @GetMapping("/{id}")
    public ResponseEntity<NutrienteResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(nutrienteService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un nuevo nutriente")
    @PostMapping
    public ResponseEntity<NutrienteResponse> crear(
            @Valid @RequestBody NutrienteRequest request) {
        NutrienteResponse creada = nutrienteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Actualizar un nutriente existente")
    @PutMapping("/{id}")
    public ResponseEntity<NutrienteResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody NutrienteRequest request) {
        return ResponseEntity.ok(nutrienteService.actualizar(id, request));
    }

    @Operation(summary = "Desactivar (baja lógica) un nutriente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        nutrienteService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Buscar nutrientes por nombre", description = "Busca coincidencias parciales ignorando mayúsculas y minúsculas.")
    @GetMapping("/buscar")
    public ResponseEntity<List<NutrienteResponse>> buscarPorNombre(
            @Parameter(description = "Texto a buscar en el nombre del nutriente")
            @RequestParam(name = "nombre") String palabraClave) {
        String busquedaLimpia = palabraClave.trim();
        if (busquedaLimpia.isEmpty()) {
            throw new IdInvalidoException("El término de búsqueda no puede estar vacío o contener solo espacios.");
        }

        return ResponseEntity.ok(nutrienteService.buscarPorNombre(busquedaLimpia));
    }

    @Operation(summary = "Buscar nutrientes por categoría")
    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<NutrienteResponse>> buscarPorCategoria(
            @PathVariable CategoriaNutriente categoria) {
        return ResponseEntity.ok(nutrienteService.buscarPorCategoria(categoria));
    }
}
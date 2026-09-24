package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import sv.edu.ues.fmp.flora.dto.request.PartePlantaRequest;
import sv.edu.ues.fmp.flora.dto.response.PartePlantaResponse;
import sv.edu.ues.fmp.flora.service.PartePlantaService;
//import sv.edu.ues.fmp.flora.service.impl.PartePlantaServiceImpl;

/**
 * API REST del catalogo de partes de planta.
 * Sin logica de negocio ni try/catch: las reglas viven en el servicio y los
 * errores los traduce el GlobalExceptionHandler.
 */
@Tag(name = "Partes de planta", description = "Catálogo de partes comestibles")
@RestController
@RequestMapping("/api/partes-planta")
@RequiredArgsConstructor
public class PartePlantaController {
    private final PartePlantaService partePlantaService;

    @Operation(summary = "Listar partes de planta",
            description = "Devuelve el catálogo completo. Con soloActivas=true omite "
                    + "las que fueron dadas de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping
    public ResponseEntity<List<PartePlantaResponse>> listar(
            @Parameter(description = "Si es true, excluye las partes desactivadas")
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {

        List<PartePlantaResponse> respuesta = soloActivas
                ? partePlantaService.listarActivas()
                : partePlantaService.listarTodas();

        return ResponseEntity.ok(respuesta);
    }

    /**
     * Va antes de {@code /{id}} por legibilidad, no por necesidad: Spring
     * resuelve primero los segmentos literales, y ademas {@code id} es Long,
     * asi que "buscar" nunca podria confundirse con un id.
     */
    @Operation(summary = "Buscar partes de planta por nombre",
            description = "Coincidencia parcial y sin distinguir mayúsculas: 'ho' encuentra "
                    + "'Hoja'. Incluye las partes desactivadas. Un término vacío devuelve "
                    + "una lista vacía, no el catálogo completo.")
    @ApiResponse(responseCode = "200",
            description = "Listado obtenido; vacío si ninguna parte coincide")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro nombre")
    @GetMapping("/buscar")
    public ResponseEntity<List<PartePlantaResponse>> buscarPorNombre(
            @Parameter(description = "Texto a buscar dentro del nombre", example = "hoja")
            @RequestParam(name = "nombre") String nombre) {

        return ResponseEntity.ok(partePlantaService.buscarPorNombre(nombre));
    }

    @Operation(summary = "Obtener una parte de planta por su id")
    @ApiResponse(responseCode = "200", description = "Parte encontrada")
    @ApiResponse(responseCode = "404", description = "No existe una parte con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<PartePlantaResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(partePlantaService.obtenerPorId(id));
    }

    @Operation(summary = "Crear una parte de planta",
            description = "El nombre es único en todo el catálogo, sin distinguir mayúsculas.")
    @ApiResponse(responseCode = "201", description = "Parte creada")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "409", description = "Ya existe una parte con ese nombre")
    @PostMapping
    public ResponseEntity<PartePlantaResponse> crear(
            @Valid @RequestBody PartePlantaRequest request) {

        PartePlantaResponse creada = partePlantaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Actualizar una parte de planta",
            description = "No cambia la bandera de estado: para dar de baja está el DELETE.")
    @ApiResponse(responseCode = "200", description = "Parte actualizada")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe una parte con ese id")
    @ApiResponse(responseCode = "409", description = "Ya existe otra parte con ese nombre")
    @PutMapping("/{id}")
    public ResponseEntity<PartePlantaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PartePlantaRequest request) {

        return ResponseEntity.ok(partePlantaService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica una parte de planta",
            description = "No borra la fila: la tabla especie_parte_comestible la referencia "
                    + "por llave foránea y un DELETE físico rompería esas referencias.")
    @ApiResponse(responseCode = "204", description = "Parte desactivada")
    @ApiResponse(responseCode = "404", description = "No existe una parte con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        partePlantaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}

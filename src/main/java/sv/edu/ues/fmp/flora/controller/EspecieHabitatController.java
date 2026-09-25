package sv.edu.ues.fmp.flora.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sv.edu.ues.fmp.flora.dto.request.EspecieHabitatRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieHabitatResponse;
import sv.edu.ues.fmp.flora.service.EspecieHabitatService;

@Tag(name = "Especie-Hábitat", description = "Hábitats asociados a cada especie")
@RestController
@RequestMapping("/api/especie-habitat")
@RequiredArgsConstructor
public class EspecieHabitatController {

    private final EspecieHabitatService especieHabitatService;

    /**
     * Devuelve todas las relaciones especie-hábitat con HTTP 200.
     */
    @Operation(summary = "Listar relaciones especie-hábitat")
    @GetMapping
    public ResponseEntity<List<EspecieHabitatResponse>> listarTodos() {
        return ResponseEntity.ok(especieHabitatService.listarTodos());
    }

    /**
     * Consulta la relación identificada por ambos IDs y devuelve HTTP 200.
     * Si el par no existe, el manejador global responde HTTP 404.
     */
    @Operation(summary = "Obtener una relación por su clave compuesta")
    @GetMapping("/{idEspecie}/{idHabitat}")
    public ResponseEntity<EspecieHabitatResponse> obtenerPorId(
            @PathVariable Long idEspecie, @PathVariable Long idHabitat) {
        return ResponseEntity.ok(especieHabitatService.obtenerPorId(idEspecie, idHabitat));
    }

    /**
     * Devuelve con HTTP 200 los hábitats asociados a la especie indicada.
     * El servicio distingue una especie inexistente de una especie sin relaciones.
     */
    @Operation(summary = "Consultar los hábitats de una especie")
    @GetMapping("/especie/{idEspecie}")
    public ResponseEntity<List<EspecieHabitatResponse>> listarPorEspecie(@PathVariable Long idEspecie) {
        return ResponseEntity.ok(especieHabitatService.listarPorEspecie(idEspecie));
    }

    /**
     * Devuelve con HTTP 200 las especies asociadas al hábitat indicado.
     * El servicio distingue un hábitat inexistente de un hábitat sin relaciones.
     */
    @Operation(summary = "Consultar las especies de un hábitat")
    @GetMapping("/habitat/{idHabitat}")
    public ResponseEntity<List<EspecieHabitatResponse>> listarPorHabitat(@PathVariable Long idHabitat) {
        return ResponseEntity.ok(especieHabitatService.listarPorHabitat(idHabitat));
    }

    /**
     * Valida el cuerpo de la solicitud y delega la creación al servicio.
     * Devuelve HTTP 201 con los datos de la relación creada.
     */
    @Operation(summary = "Crear una relación especie-hábitat")
    @PostMapping
    public ResponseEntity<EspecieHabitatResponse> crear(@Valid @RequestBody EspecieHabitatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especieHabitatService.crear(request));
    }

    /**
     * Valida la solicitud y delega la actualización de la observación.
     * El servicio comprueba que los IDs coincidan con la ruta; devuelve HTTP 200.
     */
    @Operation(summary = "Actualizar únicamente la observación de una relación")
    @PutMapping("/{idEspecie}/{idHabitat}")
    public ResponseEntity<EspecieHabitatResponse> actualizar(
            @PathVariable Long idEspecie, @PathVariable Long idHabitat,
            @Valid @RequestBody EspecieHabitatRequest request) {
        return ResponseEntity.ok(especieHabitatService.actualizar(idEspecie, idHabitat, request));
    }

    /**
     * Elimina la relación identificada por ambos IDs y devuelve HTTP 204 sin cuerpo.
     * La especie y el hábitat permanecen registrados.
     */
    @Operation(summary = "Eliminar una relación sin eliminar la especie ni el hábitat")
    @DeleteMapping("/{idEspecie}/{idHabitat}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idEspecie, @PathVariable Long idHabitat) {
        especieHabitatService.eliminar(idEspecie, idHabitat);
        return ResponseEntity.noContent().build();
    }
}

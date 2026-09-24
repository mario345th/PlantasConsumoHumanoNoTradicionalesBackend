package sv.edu.ues.fmp.flora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sv.edu.ues.fmp.flora.dto.request.MunicipioRequest;
import sv.edu.ues.fmp.flora.dto.request.MunicipioResponse;
import sv.edu.ues.fmp.flora.service.MunicipioService;

import java.util.List;

/**
 * API REST del catalogo de municipios.
 * Sin logica de negocio ni try/catch: las reglas viven en el servicio y los
 * errores los traduce el GlobalExceptionHandler.
 */
@Tag(name = "Municipios de El Salvador", description = "Municipios registrados en el país")
@RestController
@RequestMapping("/api/municipios")
@RequiredArgsConstructor
public class MunicipioController {

    private final MunicipioService service;

    @Operation(summary = "Listar municipios",
            description = "Devuelve todos los municipios del país. Con idDepartamento "
                    + "devuelve solo los de ese departamento.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404",
            description = "Se pidió filtrar por un departamento que no existe")
    @GetMapping
    public ResponseEntity<List<MunicipioResponse>> listar(
            @Parameter(description = "Filtra por departamento; si se omite, devuelve todo el país")
            @RequestParam(required = false) Long idDepartamento) {

        return ResponseEntity.ok(idDepartamento != null
                ? service.listarPorDepartamento(idDepartamento)
                : service.listarTodos());
    }

    /**
     * Va antes de {@code /{id}} por legibilidad, no por necesidad: Spring
     * resuelve primero los segmentos literales, y ademas {@code id} es Long,
     * asi que "buscar" nunca podria confundirse con un id.
     */
    @Operation(summary = "Buscar municipios por nombre",
            description = "Coincidencia parcial y sin distinguir mayúsculas: 'santa' encuentra "
                    + "'Santa Ana' y 'Santa Tecla'. Busca en todo el país e incluye los "
                    + "municipios desactivados. Un término vacío devuelve una lista vacía, "
                    + "no el catálogo completo.")
    @ApiResponse(responseCode = "200",
            description = "Listado obtenido; vacío si ningún municipio coincide")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro nombre")
    @GetMapping("/buscar")
    public ResponseEntity<List<MunicipioResponse>> buscarPorNombre(
            @Parameter(description = "Texto a buscar dentro del nombre", example = "santa")
            @RequestParam(name = "nombre") String nombre) {

        return ResponseEntity.ok(service.buscarPorNombre(nombre));
    }

    @Operation(summary = "Obtener un municipio por su id")
    @ApiResponse(responseCode = "200", description = "Municipio encontrado")
    @ApiResponse(responseCode = "404", description = "No existe un municipio con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<MunicipioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @Operation(summary = "Crear un municipio",
            description = "El nombre es único dentro de su departamento, no en todo el país: "
                    + "puede haber dos municipios llamados igual en departamentos distintos.")
    @ApiResponse(responseCode = "201", description = "Municipio creado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "El departamento indicado no existe")
    @ApiResponse(responseCode = "409",
            description = "Ese departamento ya tiene un municipio con ese nombre")
    @PostMapping
    public ResponseEntity<MunicipioResponse> crear(@Valid @RequestBody MunicipioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @Operation(summary = "Actualizar un municipio",
            description = "Permite además moverlo de departamento. No cambia la bandera de "
                    + "estado: para dar de baja está el DELETE.")
    @ApiResponse(responseCode = "200", description = "Municipio actualizado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe el municipio o el departamento")
    @ApiResponse(responseCode = "409",
            description = "Ese departamento ya tiene otro municipio con ese nombre")
    @PutMapping("/{id}")
    public ResponseEntity<MunicipioResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody MunicipioRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un municipio",
            description = "No borra la fila: la tabla distrito la referencia por llave "
                    + "foránea y un DELETE físico rompería esas referencias.")
    @ApiResponse(responseCode = "204", description = "Municipio desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un municipio con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}

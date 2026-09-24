package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.service.NombreComunService;

/**
 * Rutas anidadas de los nombres comunes bajo su especie.
 * <p>
 * Listar y crear siempre ocurren en el contexto de una especie, y la URL lo
 * refleja: sin ella, un POST tendria que llevar el {@code idEspecie} en el
 * cuerpo y no habria forma de leer la coleccion completa.
 * <p>
 * Va en su propia clase y no dentro de {@link EspecieController} para que el
 * modulo de nombres comunes quede completo en si mismo: el controlador de
 * especies ya carga el flujo editorial y no necesita crecer con recursos hijos.
 * Sin logica de negocio ni try/catch.
 */
@Tag(name = "Nombres comunes", description = "Nombres vernáculos de una especie")
@RestController
@RequestMapping("/api/especies/{idEspecie}/nombres-comunes")
@RequiredArgsConstructor
public class EspecieNombreComunController {

    private final NombreComunService nombreComunService;

    @Operation(summary = "Listar los nombres comunes de una especie")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "404", description = "La especie no existe")
    @GetMapping
    public ResponseEntity<List<NombreComunResponse>> listar(
            @PathVariable Long idEspecie,
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<NombreComunResponse> respuesta = soloActivos
                ? nombreComunService.listarActivosPorEspecie(idEspecie)
                : nombreComunService.listarPorEspecie(idEspecie);

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Agregar un nombre común a una especie")
    @ApiResponse(responseCode = "201", description = "Nombre común creado")
    @ApiResponse(responseCode = "409", description = "La especie ya tiene ese nombre")
    @PostMapping
    public ResponseEntity<NombreComunResponse> crear(
            @PathVariable Long idEspecie,
            @Valid @RequestBody NombreComunRequest request) {

        NombreComunResponse creado = nombreComunService.crear(idEspecie, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}

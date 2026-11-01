package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieParteComestibleRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieParteComestibleResponse;
import sv.edu.ues.fmp.flora.service.EspecieParteComestibleService;

/**
 * Rutas anidadas de las partes comestibles bajo su especie: listar y crear
 * siempre ocurren en el contexto de una especie. Las operaciones sobre un
 * registro ya existente van en {@link EspecieParteComestibleController}.
 * Sin logica de negocio ni try/catch.
 */
@Tag(name = "Partes comestibles", description = "Partes comestibles de cada especie")
@RestController
@RequestMapping("/api/especies/{idEspecie}/partes-comestibles")
@RequiredArgsConstructor
public class EspecieParteComestibleAnidadoController {

    private final EspecieParteComestibleService especieParteComestibleService;

    @Operation(summary = "Listar las partes comestibles de una especie",
            description = "Ordenadas por nombre de la parte. Con soloActivas=true omite "
                    + "las que fueron dadas de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400",
            description = "idEspecie no es numérico o soloActivas no es true/false")
    @ApiResponse(responseCode = "404", description = "La especie no existe")
    @GetMapping
    public ResponseEntity<List<EspecieParteComestibleResponse>> listar(
            @PathVariable Long idEspecie,
            @Parameter(description = "Si es true, excluye las partes desactivadas")
            @RequestParam(name = "soloActivas", defaultValue = "false") boolean soloActivas) {

        return ResponseEntity.ok(especieParteComestibleService.listarPorEspecie(idEspecie, soloActivas));
    }

    @Operation(summary = "Registrar una parte comestible en una especie",
            description = "La combinación especie + parte es única en todo el historial, "
                    + "activa o no. Si ya existe desactivada, hay que reactivarla con "
                    + "PATCH /api/partes-comestibles/{id}/activar en lugar de crearla otra vez.")
    @ApiResponse(responseCode = "201", description = "Parte comestible registrada")
    @ApiResponse(responseCode = "400",
            description = "idEspecie no es numérico, el JSON está mal formado, un campo tiene "
                    + "un tipo de dato incorrecto o falta idPartePlanta")
    @ApiResponse(responseCode = "404", description = "La especie o la parte de planta no existe")
    @ApiResponse(responseCode = "409",
            description = "La especie ya tiene esa parte, o la especie o la parte del catálogo "
                    + "están desactivadas")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @PostMapping
    public ResponseEntity<EspecieParteComestibleResponse> crear(
            @PathVariable Long idEspecie,
            @Valid @RequestBody EspecieParteComestibleRequest request) {

        EspecieParteComestibleResponse creada = especieParteComestibleService.crear(idEspecie, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}

package sv.edu.ues.fmp.flora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieParteComestibleActualizarRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieParteComestibleResponse;
import sv.edu.ues.fmp.flora.service.EspecieParteComestibleService;


@Tag(name = "Partes comestibles", description = "Partes comestibles de cada especie")
@RestController
@RequestMapping("/api/partes-comestibles")
@RequiredArgsConstructor
public class EspecieParteComestibleController {

    private final EspecieParteComestibleService especieParteComestibleService;

    @Operation(summary = "Obtener una parte comestible por su id")
    @ApiResponse(responseCode = "200", description = "Parte comestible encontrada")
    @ApiResponse(responseCode = "400", description = "El id no tiene formato numérico")
    @ApiResponse(responseCode = "404", description = "No existe una parte comestible con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<EspecieParteComestibleResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(especieParteComestibleService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar una parte comestible",
            description = "Solo edita descripción y advertencias. Reemplaza descripcion y "
                    + "advertencias: un campo omitido o nulo queda vacío. Envíe ambos campos "
                    + "aunque solo quiera cambiar uno. La parte de planta asociada "
                    + "NO puede cambiarse: preparaciones, aportes nutricionales, épocas de "
                    + "cosecha, beneficios, imágenes y videos cuelgan de este registro, y "
                    + "cambiarla los movería a otra parte. Enviar idPartePlanta con valor se "
                    + "rechaza con 400. Para otra parte, desactive esta y registre una nueva.")
    @ApiResponse(responseCode = "200", description = "Parte comestible actualizada")
    @ApiResponse(responseCode = "400",
            description = "El id no tiene formato numérico, el cuerpo no es válido, o se envió "
                    + "idPartePlanta: la parte no se puede modificar "
                    + "(erroresValidacion.idPartePlanta)")
    @ApiResponse(responseCode = "404", description = "No existe una parte comestible con ese id")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @PutMapping("/{id}")
    public ResponseEntity<EspecieParteComestibleResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EspecieParteComestibleActualizarRequest request) {

        return ResponseEntity.ok(especieParteComestibleService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica una parte comestible",
            description = "No borra la fila: seis tablas la referencian por llave foránea. "
                    + "Repetir la baja no es un error.")
    @ApiResponse(responseCode = "204", description = "Parte comestible desactivada")
    @ApiResponse(responseCode = "400", description = "El id no tiene formato numérico")
    @ApiResponse(responseCode = "404", description = "No existe una parte comestible con ese id")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        especieParteComestibleService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Se usa PATCH y no PUT porque solo cambia la bandera de estado, sin tocar
     * el resto del recurso.
     */
    @Operation(summary = "Reactivar una parte comestible dada de baja",
            description = "Idempotente: si ya está activa se devuelve sin cambios.")
    @ApiResponse(responseCode = "200", description = "Parte comestible activa")
    @ApiResponse(responseCode = "400", description = "El id no tiene formato numérico")
    @ApiResponse(responseCode = "404", description = "No existe una parte comestible con ese id")
    @ApiResponse(responseCode = "409", description = "La parte del catálogo está desactivada")
    @PreAuthorize("hasAuthority('PARTE_GESTIONAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<EspecieParteComestibleResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(especieParteComestibleService.activar(id));
    }
}

package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieRequest;
import sv.edu.ues.fmp.flora.dto.request.TaxonomiaRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieResponse;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.service.EspecieService;

/**
 * API REST de las especies y su flujo de publicacion.
 * Sin logica de negocio ni try/catch: las reglas viven en el servicio y los
 * errores los traduce el GlobalExceptionHandler.
 */
@Tag(name = "Especies", description = "Fichas de especies y su flujo de publicación con su TAXONOMÍA")
@RestController
@RequestMapping("/api/especies")
@RequiredArgsConstructor
public class EspecieController {

    private final EspecieService especieService;

    @Operation(summary = "Listar especies, opcionalmente filtradas por estado",
            description = "Vista interna: devuelve fichas en cualquier estado, BORRADOR y "
                    + "EN_REVISION incluidos, por lo que exige ESPECIE_CONSULTAR. El catálogo "
                    + "de cara al público es GET /api/especies/publicadas, que no pide token.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "401", description = "Falta el token")
    @ApiResponse(responseCode = "403", description = "El token no trae ESPECIE_CONSULTAR")
    @PreAuthorize("hasAuthority('ESPECIE_CONSULTAR')")
    @GetMapping
    public ResponseEntity<List<EspecieResponse>> listar(
            @RequestParam(name = "estado", required = false) EstadoPublicacion estado) {

        List<EspecieResponse> respuesta = (estado == null)
                ? especieService.listarTodas()
                : especieService.listarPorEstado(estado);

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Catálogo público: solo especies publicadas y activas")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @GetMapping("/publicadas")
    public ResponseEntity<List<EspecieResponse>> listarPublicadas() {
        return ResponseEntity.ok(especieService.listarPublicadas());
    }

    @Operation(summary = "Catálogo público: detalle de una especie publicada",
            description = "Único detalle por id que no pide token. Si la ficha no está "
                    + "PUBLICADA y activa responde 404, el mismo código y el mismo mensaje "
                    + "que un id inexistente, para que no sirva para averiguar qué fichas "
                    + "hay en preparación.")
    @ApiResponse(responseCode = "200", description = "Ficha obtenida")
    @ApiResponse(responseCode = "404", description = "No hay una especie publicada con ese id")
    @GetMapping("/publicadas/{id}")
    public ResponseEntity<EspecieResponse> obtenerPublicadaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(especieService.obtenerPublicadaPorId(id));
    }

    @Operation(summary = "Obtener una especie por su id, en cualquier estado",
            description = "Vista interna: responde igual para una ficha publicada que para "
                    + "una en BORRADOR, por lo que exige ESPECIE_CONSULTAR. El detalle "
                    + "público es GET /api/especies/publicadas/{id}.")
    @ApiResponse(responseCode = "401", description = "Falta el token")
    @ApiResponse(responseCode = "403", description = "El token no trae ESPECIE_CONSULTAR")
    @PreAuthorize("hasAuthority('ESPECIE_CONSULTAR')")
    @GetMapping("/{id}")
    public ResponseEntity<EspecieResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(especieService.obtenerPorId(id));
    }

    @Operation(summary = "Crear una especie junto con su taxonomía",
            description = "El estado inicial siempre es BORRADOR. Cualquier campo "
                    + "estadoPublicacion, activa, validadaPor, publicadaPor o fechas "
                    + "enviados en el cuerpo son ignorados silenciosamente; el flujo "
                    + "editorial se gestiona con los endpoints PATCH dedicados "
                    + "(enviar-revision, validar, publicar, rechazar).")
    @PreAuthorize("hasAuthority('ESPECIE_CREAR')")
    @PostMapping
    public ResponseEntity<EspecieResponse> crear(@Valid @RequestBody EspecieRequest request) {
        EspecieResponse creada = especieService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Actualizar los datos editables de una especie y su taxonomía")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<EspecieResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EspecieRequest request) {

        return ResponseEntity.ok(especieService.actualizar(id, request));
    }

    /**
     * Se usa PATCH y no PUT porque es una actualizacion parcial del recurso
     * especie: modifica su clasificacion sin tocar la ficha.
     */
    @Operation(summary = "Rectificar solo la clasificación taxonómica de una especie")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PatchMapping("/{id}/taxonomia")
    public ResponseEntity<EspecieResponse> actualizarTaxonomia(
            @PathVariable Long id,
            @Valid @RequestBody TaxonomiaRequest request) {

        return ResponseEntity.ok(especieService.actualizarTaxonomia(id, request));
    }

    @Operation(summary = "Enviar la ficha a revisión")
    @PreAuthorize("hasAuthority('ESPECIE_VALIDAR')")
    @PatchMapping("/{id}/enviar-revision")
    public ResponseEntity<EspecieResponse> enviarARevision(@PathVariable Long id) {
        return ResponseEntity.ok(especieService.enviarARevision(id));
    }

    @Operation(summary = "Marcar la ficha como validada, sin publicarla")
    @PreAuthorize("hasAuthority('ESPECIE_VALIDAR')")
    @PatchMapping("/{id}/validar")
    public ResponseEntity<EspecieResponse> validar(
            @PathVariable Long id,
            // TODO: eliminar el parámetro cuando se implemente JWT; el validador
            // se tomará del token del usuario autenticado
            @RequestParam(name = "idValidador") Long idValidador) {

        return ResponseEntity.ok(especieService.validar(id, idValidador));
    }

    @Operation(summary = "Publicar una ficha ya validada")
    @PreAuthorize("hasAuthority('ESPECIE_PUBLICAR')")
    @PatchMapping("/{id}/publicar")
    public ResponseEntity<EspecieResponse> publicar(
            @PathVariable Long id,
            // TODO: eliminar el parámetro cuando se implemente JWT; el publicador
            // se tomará del token del usuario autenticado
            @RequestParam(name = "idPublicador") Long idPublicador) {

        return ResponseEntity.ok(especieService.publicar(id, idPublicador));
    }

    @Operation(summary = "Rechazar una ficha en revisión")
    @PreAuthorize("hasAuthority('ESPECIE_VALIDAR')")
    @PatchMapping("/{id}/rechazar")
    public ResponseEntity<EspecieResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(especieService.rechazar(id));
    }

    @Operation(summary = "Dar de baja lógica una especie")
    @ApiResponse(responseCode = "204", description = "Especie desactivada")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        especieService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activar una especie desactivada",
            description = "Revierte la baja lógica. Es idempotente: sobre una especie que ya "
                    + "está activa no cambia nada y devuelve 200 igualmente. No modifica el "
                    + "estado de publicación: si la especie estaba PUBLICADA, vuelve al "
                    + "catálogo público de inmediato.")
    @ApiResponse(responseCode = "200", description = "Especie activa")
    @ApiResponse(responseCode = "404", description = "No existe una especie con ese id")
    @PreAuthorize("hasAuthority('ESPECIE_EDITAR')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<EspecieResponse> activar(@PathVariable Long id) {
        return ResponseEntity.ok(especieService.activar(id));
    }
}

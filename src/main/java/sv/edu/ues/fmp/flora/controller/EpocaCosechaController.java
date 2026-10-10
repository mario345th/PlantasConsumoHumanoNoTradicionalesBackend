package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaActualizarRequest;
import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaRequest;
import sv.edu.ues.fmp.flora.dto.response.EpocaCosechaResponse;
import sv.edu.ues.fmp.flora.service.EpocaCosechaService;

@RestController
@RequestMapping("/api/epocas-cosecha")
@RequiredArgsConstructor
public class EpocaCosechaController {

    private final EpocaCosechaService epocaCosechaService;

    @GetMapping
    public ResponseEntity<List<EpocaCosechaResponse>> listar(
            @RequestParam Long idEspecieParte,
            @RequestParam(defaultValue = "false") boolean soloActivas
    ) {
        return ResponseEntity.ok(
                epocaCosechaService.listarPorParte(
                        idEspecieParte,
                        soloActivas
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<EpocaCosechaResponse> obtenerPorId(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                epocaCosechaService.obtenerPorId(id)
        );
    }

    @PostMapping
    public ResponseEntity<EpocaCosechaResponse> crear(
            @Valid @RequestBody EpocaCosechaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(epocaCosechaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EpocaCosechaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EpocaCosechaActualizarRequest request
    ) {
        return ResponseEntity.ok(
                epocaCosechaService.actualizar(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        epocaCosechaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<EpocaCosechaResponse> activar(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                epocaCosechaService.activar(id)
        );
    }
}
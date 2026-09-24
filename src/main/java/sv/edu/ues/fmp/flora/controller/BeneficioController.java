package sv.edu.ues.fmp.flora.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sv.edu.ues.fmp.flora.dto.request.BeneficioRequest;
import sv.edu.ues.fmp.flora.dto.response.BeneficioResponse;
import sv.edu.ues.fmp.flora.service.BeneficioService;

import java.util.List;

@RestController
@RequestMapping("/api/beneficios")
@RequiredArgsConstructor
public class BeneficioController {

    private final BeneficioService beneficioService;

    /**
     * Listar todos los beneficios.
     */
    @GetMapping
    public ResponseEntity<List<BeneficioResponse>>
    listarTodos() {

        return ResponseEntity.ok(
                beneficioService.listarTodos()
        );
    }

    /**
     * Buscar beneficio por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BeneficioResponse>
    obtenerPorId(@PathVariable Long id) {

        return ResponseEntity.ok(
                beneficioService.obtenerPorId(id)
        );
    }

    /**
     * Crear beneficio.
     */
    @PostMapping
    public ResponseEntity<BeneficioResponse>
    crear(
            @Valid
            @RequestBody BeneficioRequest request
    ) {

        BeneficioResponse beneficio =
                beneficioService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(beneficio);
    }

    /**
     * Actualizar beneficio.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BeneficioResponse>
    actualizar(
            @PathVariable Long id,
            @Valid
            @RequestBody BeneficioRequest request
    ) {

        return ResponseEntity.ok(
                beneficioService.actualizar(
                        id,
                        request
                )
        );
    }

    /**
     * Eliminar beneficio.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void>
    eliminar(@PathVariable Long id) {

        beneficioService.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}
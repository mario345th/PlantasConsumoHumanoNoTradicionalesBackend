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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.DepartamentoRequest;
import sv.edu.ues.fmp.flora.dto.response.DepartamentoResponse;
import sv.edu.ues.fmp.flora.service.DepartamentoService;

@RestController
@RequestMapping("/api/departamentos")
@RequiredArgsConstructor
public class DepartamentoController {

    /** Capa HTTP: delega las reglas de negocio al servicio. */
    private final DepartamentoService departamentoService;

    /** Devuelve la colección completa de departamentos. */
    @GetMapping
    public ResponseEntity<List<DepartamentoResponse>> listarTodos() {
        return ResponseEntity.ok(departamentoService.listarTodos());
    }

    /** Devuelve un departamento identificado por la variable de ruta. */
    @GetMapping("/{id}")
    public ResponseEntity<DepartamentoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(departamentoService.obtenerPorId(id));
    }

    /** Valida la solicitud y responde 201 cuando el departamento se crea. */
    @PostMapping
    public ResponseEntity<DepartamentoResponse> crear(
            @Valid @RequestBody DepartamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departamentoService.crear(request));
    }

    /** Valida y actualiza el departamento solicitado. */
    @PutMapping("/{id}")
    public ResponseEntity<DepartamentoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody DepartamentoRequest request) {
        return ResponseEntity.ok(departamentoService.actualizar(id, request));
    }

    /** Ejecuta la baja lógica y responde 204 sin cuerpo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        departamentoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}

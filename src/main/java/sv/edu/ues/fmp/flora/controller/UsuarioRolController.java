package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;

@RestController
@RequestMapping("/api/usuario-roles")
@RequiredArgsConstructor
public class UsuarioRolController {

    private final UsuarioRolService usuarioRolService;

    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<List<RolResponse>> obtenerRolesDeUsuario(@PathVariable Long idUsuario) {
        return ResponseEntity.ok(usuarioRolService.obtenerRolesDeUsuario(idUsuario));
    }
}

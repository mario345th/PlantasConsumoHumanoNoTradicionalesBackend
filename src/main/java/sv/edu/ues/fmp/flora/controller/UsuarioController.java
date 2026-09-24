package sv.edu.ues.fmp.flora.controller;

import java.util.List;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCambioClaveRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCreationRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.service.UsuarioService;

@Tag(name = "Usuarios", description = "Cuentas del personal interno del sistema "
        + "(investigadores y administradores) y su autenticación")
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Listar usuarios", description = "Devuelve el catálogo completo. Con soloActivos=true omite "
                    + "las cuentas dadas de baja lógica.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400", description = "Parametro invalido")
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar(
            @Parameter(description = "Si es true, excluye las cuentas desactivadas")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<UsuarioResponse> respuesta = soloActivos
                ? usuarioService.listarActivos()
                : usuarioService.listarTodos();

        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Obtener un usuario por su id")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "400", description = "Id invalido")
    @ApiResponse(responseCode = "400", description = "Parametro invalido")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un usuario",
            description = "correo y nombreUsuario son únicos en todo el catálogo, sin "
                    + "distinguir mayúsculas. La clave nunca se persiste en texto plano: "
                    + "se guarda como clave_hash.")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo o nombre de usuario")
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioCreationRequest request) {
        UsuarioResponse creado = usuarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Actualizar los datos de un usuario",
            description = "No cambia la clave ni la bandera de estado: para eso está el PUT "
                    + "/clave, y para dar de baja o reactivar están el DELETE y el "
                    + "PATCH /activar.")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @ApiResponse(responseCode = "409",
            description = "Ya existe otro usuario con ese correo o nombre de usuario")
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request) {

        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    @Operation(summary = "Dar de baja lógica un usuario",
            description = "No borra la fila: especie la referencia por creada_por, "
                    + "validada_por y publicada_por, y un DELETE físico rompería esas "
                    + "referencias. Una cuenta inactiva no puede iniciar sesión.")
    @ApiResponse(responseCode = "204", description = "Usuario desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar un usuario dado de baja",
            description = "Vuelve a poner activo=true. Los roles que tenía en usuario_rol "
                    + "nunca se borraron al desactivarlo, así que puede volver a iniciar "
                    + "sesión con las mismas credenciales de siempre.")
    @ApiResponse(responseCode = "200", description = "Usuario reactivado")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<UsuarioResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.reactivar(id));
    }

    @Operation(summary = "Cambiar la contraseña de un usuario",
            description = "Exige la clave actual, no solo el id: como todavía no hay Spring "
                    + "Security validando quién hace la petición, esta es la única barrera "
                    + "contra que alguien le cambie la clave a otra cuenta.")
    @ApiResponse(responseCode = "204", description = "Clave actualizada")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "401", description = "La clave actual no es correcta")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @PutMapping("/{id}/clave")
    public ResponseEntity<Void> cambiarClave(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioCambioClaveRequest request) {

        usuarioService.cambiarClave(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Iniciar sesión",
            description = "El campo usuario acepta indistintamente el correo o el nombre de "
                    + "usuario. Si la cuenta existe, está activa y la clave coincide, "
                    + "actualiza ultimo_acceso y devuelve el usuario. El mensaje de error es "
                    + "siempre el mismo para no revelar cuál de las tres cosas falló.")
    @ApiResponse(responseCode = "200", description = "Autenticación exitosa")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "401",
            description = "Usuario o clave incorrectos, o la cuenta está desactivada")
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> iniciarSesion(@Valid @RequestBody UsuarioLoginRequest request) {
        return ResponseEntity.ok(usuarioService.iniciarSesion(request));
    }
}

package sv.edu.ues.fmp.flora.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.response.LoginResponse;
import sv.edu.ues.fmp.flora.service.AuthService;

/**
 * Autenticacion por token.
 * <p>
 * Convive con {@code POST /api/usuarios/login}, que sigue publico y sigue
 * devolviendo solo el usuario, sin token, para no romper al frontend mientras
 * migra. Cuando el frontend ya no lo llame, ese endpoint se puede retirar: es
 * una conversacion con Mario y con el equipo, no algo que esta rama decida.
 * <p>
 * {@code GET /api/auth/me} y los refresh tokens quedan fuera de alcance por
 * ahora.
 */
@Tag(name = "Autenticación", description = "Emisión de tokens de acceso (JWT)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Iniciar sesión y obtener un token",
            description = "El campo usuario acepta indistintamente el correo o el nombre de "
                    + "usuario. Devuelve un JWT firmado que el cliente debe mandar en la "
                    + "cabecera Authorization: Bearer <token> en las peticiones a endpoints "
                    + "protegidos. El token lleva dentro el id del usuario (sub), su nombre "
                    + "de usuario, sus roles vigentes y sus permisos efectivos; caduca según "
                    + "app.jwt.expiration-minutes. El mensaje de error es siempre el mismo "
                    + "para no revelar si falló el usuario, la clave o el estado de la cuenta.")
    @ApiResponse(responseCode = "200", description = "Autenticación exitosa")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "401",
            description = "Usuario o clave incorrectos, o la cuenta está desactivada")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> iniciarSesion(
            @Valid @RequestBody UsuarioLoginRequest request) {

        return ResponseEntity.ok(authService.iniciarSesion(request));
    }
}

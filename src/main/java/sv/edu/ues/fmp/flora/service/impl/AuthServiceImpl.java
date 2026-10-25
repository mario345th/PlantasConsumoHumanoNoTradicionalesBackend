package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.response.LoginResponse;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.service.AuthService;
import sv.edu.ues.fmp.flora.service.JwtService;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;
import sv.edu.ues.fmp.flora.service.UsuarioService;

/**
 * Orquesta el login: no valida la clave por su cuenta ni consulta
 * repositorios, solo encadena servicios que ya existian.
 * <p>
 * La comprobacion BCrypt sigue viviendo en
 * {@link UsuarioService#iniciarSesion}, que es la que ya hacia
 * {@code POST /api/usuarios/login}: busca por correo o nombre de usuario,
 * exige que la cuenta este activa, compara contra {@code claveHash} con el
 * {@code PasswordEncoder} compartido, actualiza {@code ultimo_acceso} y lanza
 * {@code CredencialesInvalidasException} con un mensaje unico cuando algo
 * falla. Duplicar aqui ese codigo habria significado mantener dos reglas de
 * autenticacion en paralelo.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioService usuarioService;
    private final UsuarioRolService usuarioRolService;
    private final JwtService jwtService;

    @Override
    @Transactional
    public LoginResponse iniciarSesion(UsuarioLoginRequest request) {
        // Si las credenciales no sirven, esto lanza
        // CredencialesInvalidasException y el GlobalExceptionHandler la
        // convierte en el 401 con el ErrorResponse del proyecto. No hay que
        // atraparla aqui.
        UsuarioResponse usuario = usuarioService.iniciarSesion(request);

        // soloActivos=true: un rol dado de baja no otorga nada, asi que no
        // tiene por que entrar en el token.
        List<String> roles = usuarioRolService.obtenerRolesDeUsuario(usuario.getId(), true)
                .stream()
                .map(RolResponse::getNombre)
                .toList();

        List<String> permisos = usuarioRolService.obtenerPermisosEfectivosDeUsuario(usuario.getId())
                .stream()
                .map(PermisoResponse::getCodigo)
                .toList();

        JwtService.TokenEmitido emitido = jwtService.emitir(usuario, roles, permisos);

        return LoginResponse.builder()
                .token(emitido.token())
                .tipo("Bearer")
                .expiraEn(emitido.expiraEn())
                .usuario(usuario)
                .build();
    }
}

package sv.edu.ues.fmp.flora.service;

import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.response.LoginResponse;

public interface AuthService {

    /**
     * Valida las credenciales y, si son correctas, devuelve un token de
     * acceso junto con los datos publicos del usuario.
     *
     * @throws sv.edu.ues.fmp.flora.exception.CredencialesInvalidasException
     *         si el usuario no existe, esta desactivado o la clave no coincide
     */
    LoginResponse iniciarSesion(UsuarioLoginRequest request);
}

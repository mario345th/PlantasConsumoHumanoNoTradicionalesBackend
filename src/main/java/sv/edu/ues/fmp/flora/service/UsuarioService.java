package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.UsuarioCambioClaveRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCreationRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;

public interface UsuarioService {

    List<UsuarioResponse> listarTodos();

    List<UsuarioResponse> listarActivos();

    UsuarioResponse obtenerPorId(Long id);

    UsuarioResponse crear(UsuarioCreationRequest request);

    UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request);

    void desactivar(Long id);

    UsuarioResponse reactivar(Long id);

    void cambiarClave(Long id, UsuarioCambioClaveRequest request);

    UsuarioResponse iniciarSesion(UsuarioLoginRequest request);
}

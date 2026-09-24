package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.UsuarioRolRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioRolResponse;

public interface UsuarioRolService {

    List<RolResponse> obtenerRolesDeUsuario(Long idUsuario);

    List<PermisoResponse> obtenerPermisosEfectivosDeUsuario(Long idUsuario);

    UsuarioRolResponse asignar(UsuarioRolRequest request);

    void quitar(Long idUsuario, Long idRol);
}

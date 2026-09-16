package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.response.RolResponse;

public interface UsuarioRolService {

    List<RolResponse> obtenerRolesDeUsuario(Long idUsuario);
}

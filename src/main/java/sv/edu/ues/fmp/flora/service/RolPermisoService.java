package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.RolPermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolPermisoResponse;

public interface RolPermisoService {

    List<PermisoResponse> obtenerPermisosDeRol(Long idRol);

    RolPermisoResponse asignar(RolPermisoRequest request);

    void quitar(Long idRol, Long idPermiso);
}

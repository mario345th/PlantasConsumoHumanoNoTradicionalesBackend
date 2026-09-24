package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.PermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;

public interface PermisoService {

    List<PermisoResponse> listarTodos();

    List<PermisoResponse> listarActivos();

    PermisoResponse obtenerPorId(Long id);

    PermisoResponse crear(PermisoRequest request);

    PermisoResponse actualizar(Long id, PermisoRequest request);

    void desactivar(Long id);

    PermisoResponse reactivar(Long id);
}

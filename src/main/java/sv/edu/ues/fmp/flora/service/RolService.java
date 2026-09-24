package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.RolRequest;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;

public interface RolService {

    List<RolResponse> listarTodos();

    List<RolResponse> listarActivos();

    RolResponse obtenerPorId(Long id);

    RolResponse crear(RolRequest request);

    RolResponse actualizar(Long id, RolRequest request);

    void desactivar(Long id);

    RolResponse reactivar(Long id);
}

package sv.edu.ues.fmp.flora.service;

import java.util.List;
import sv.edu.ues.fmp.flora.dto.request.BeneficioRequest;
import sv.edu.ues.fmp.flora.dto.response.BeneficioResponse;

public interface BeneficioService {

    List<BeneficioResponse> listarTodos();

    BeneficioResponse obtenerPorId(Long id);

    BeneficioResponse crear(BeneficioRequest request);

    BeneficioResponse actualizar(Long id, BeneficioRequest request);

    void eliminar(Long id);
}
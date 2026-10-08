package sv.edu.ues.fmp.flora.service;

import java.util.List;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoRequest;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.PreparacionConsumoResponse;

public interface PreparacionConsumoService {
    List<PreparacionConsumoResponse> listar(boolean soloActivas);
    List<PreparacionConsumoResponse> listarPorParte(Long idParte, boolean soloActivas);
    PreparacionConsumoResponse obtenerPorId(Long id);
    PreparacionConsumoResponse crear(PreparacionConsumoRequest request);
    PreparacionConsumoResponse actualizar(Long id, PreparacionConsumoUpdateRequest request);
    void desactivar(Long id);
}

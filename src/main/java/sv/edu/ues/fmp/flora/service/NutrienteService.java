package sv.edu.ues.fmp.flora.service;

import java.util.List;
import sv.edu.ues.fmp.flora.dto.request.NutrienteRequest;
import sv.edu.ues.fmp.flora.dto.response.NutrienteResponse;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;

public interface NutrienteService {
    List<NutrienteResponse> listarTodas();
    List<NutrienteResponse> listarActivas();
    NutrienteResponse obtenerPorId(Long id);
    NutrienteResponse crear(NutrienteRequest request);
    NutrienteResponse actualizar(Long id, NutrienteRequest request);
    void desactivar(Long id);
    List<NutrienteResponse> buscarPorNombre(String palabraClave);
    List<NutrienteResponse> buscarPorCategoria(CategoriaNutriente categoria);
}
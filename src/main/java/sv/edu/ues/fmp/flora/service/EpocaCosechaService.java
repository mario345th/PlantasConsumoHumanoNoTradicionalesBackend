package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaActualizarRequest;
import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaRequest;
import sv.edu.ues.fmp.flora.dto.response.EpocaCosechaResponse;

public interface EpocaCosechaService {

    List<EpocaCosechaResponse> listarPorParte(
            Long idEspecieParte,
            boolean soloActivas
    );

    EpocaCosechaResponse obtenerPorId(Long id);

    EpocaCosechaResponse crear(EpocaCosechaRequest request);

    EpocaCosechaResponse actualizar(
            Long id,
            EpocaCosechaActualizarRequest request
    );

    void desactivar(Long id);

    EpocaCosechaResponse activar(Long id);
}
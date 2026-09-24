package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.PartePlantaRequest;
import sv.edu.ues.fmp.flora.dto.response.PartePlantaResponse;

/**
 * Contrato de negocio para las partes de planta.
 * Trabaja solo con DTOs: la entidad no cruza hacia el controlador.
 */

//contrato
public interface PartePlantaService {

    List<PartePlantaResponse> listarTodas();

    List<PartePlantaResponse> listarActivas();

    PartePlantaResponse obtenerPorId(Long id);

    /**
     * Busqueda parcial por nombre, sin distinguir mayusculas. Devuelve activas
     * e inactivas: quien busca quiere encontrar la parte aunque este dada de
     * baja, aunque solo sea para reactivarla.
     */
    List<PartePlantaResponse> buscarPorNombre(String nombre);

    PartePlantaResponse crear(PartePlantaRequest request);

    PartePlantaResponse actualizar(Long id, PartePlantaRequest request);

    void desactivar(Long id);



}

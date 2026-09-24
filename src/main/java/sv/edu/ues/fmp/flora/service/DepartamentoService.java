package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.DepartamentoRequest;
import sv.edu.ues.fmp.flora.dto.response.DepartamentoResponse;

public interface DepartamentoService {

    /** Recupera todos los departamentos, independientemente de su estado. */
    List<DepartamentoResponse> listarTodos();

    /** Obtiene un departamento por su identificador o informa que no existe. */
    DepartamentoResponse obtenerPorId(Long id);

    /** Crea un departamento a partir de los datos validados del cliente. */
    DepartamentoResponse crear(DepartamentoRequest request);

    /** Actualiza los datos editables del departamento indicado. */
    DepartamentoResponse actualizar(Long id, DepartamentoRequest request);

    /** Realiza una baja lógica para conservar relaciones existentes. */
    void desactivar(Long id);
}

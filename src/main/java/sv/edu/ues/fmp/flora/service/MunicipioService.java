package sv.edu.ues.fmp.flora.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import sv.edu.ues.fmp.flora.dto.request.MunicipioRequest;
import sv.edu.ues.fmp.flora.dto.request.MunicipioResponse;

import java.util.List;

public interface MunicipioService {


     List<MunicipioResponse> listarTodos();

     List<MunicipioResponse> listarPorDepartamento(Long idDepartamento);

     /**
      * Busqueda parcial por nombre en todo el pais, sin distinguir mayusculas.
      * Devuelve activos e inactivos: quien busca quiere encontrar el municipio
      * aunque este dado de baja, aunque solo sea para reactivarlo.
      */
     List<MunicipioResponse> buscarPorNombre(String nombre);

     MunicipioResponse obtenerPorId(Long id);

     MunicipioResponse crear(MunicipioRequest request);

     MunicipioResponse actualizar(Long id,MunicipioRequest request);

     void desactivar(Long id);

}

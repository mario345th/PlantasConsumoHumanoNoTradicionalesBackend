package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;
import sv.edu.ues.fmp.flora.dto.request.DepartamentoRequest;
import sv.edu.ues.fmp.flora.dto.response.DepartamentoResponse;
import sv.edu.ues.fmp.flora.entity.Departamento;

@Component
public class DepartamentoMapper {

    /** Convierte los datos validados de entrada en una entidad lista para guardar. */
    public Departamento toEntity(DepartamentoRequest request) {
        return Departamento.builder()
                .nombre(request.getNombre())
                .build();
    }

    /** Copia los campos editables sobre la entidad administrada por JPA. */
    public void updateEntity(Departamento entity, DepartamentoRequest request) {
        entity.setNombre(request.getNombre());
    }

    /** Proyecta la entidad al formato que se expone desde la API. */
    public DepartamentoResponse toResponse(Departamento entity) {
        return DepartamentoResponse.builder()
                .idDepartamento(entity.getIdDepartamento())
                .nombre(entity.getNombre())
                .activo(entity.getActivo())
                .build();
    }
}

package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.dto.request.PermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.entity.Permiso;

@Component
public class PermisoMapper {

    public Permiso toEntity(PermisoRequest request) {
        return Permiso.builder()
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .build();
    }

    public void updateEntity(Permiso entity, PermisoRequest request) {
        entity.setCodigo(request.getCodigo());
        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
    }

    public PermisoResponse toResponse(Permiso entity) {
        return PermisoResponse.builder()
                .id(entity.getIdPermiso())
                .codigo(entity.getCodigo())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .activo(entity.getActivo())
                .build();
    }
}

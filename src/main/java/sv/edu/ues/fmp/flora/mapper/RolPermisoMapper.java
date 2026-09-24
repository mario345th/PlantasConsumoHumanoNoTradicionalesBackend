package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.dto.response.RolPermisoResponse;
import sv.edu.ues.fmp.flora.entity.RolPermiso;

@Component
public class RolPermisoMapper {

    public RolPermisoResponse toResponse(RolPermiso entity) {
        return RolPermisoResponse.builder()
                .idRol(entity.getRol().getIdRol())
                .idPermiso(entity.getPermiso().getIdPermiso())
                .codigoPermiso(entity.getPermiso().getCodigo())
                .nombrePermiso(entity.getPermiso().getNombre())
                .build();
    }
}

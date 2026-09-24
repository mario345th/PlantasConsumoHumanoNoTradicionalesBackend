package sv.edu.ues.fmp.flora.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioRolResponse;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.UsuarioRol;

@Component
@RequiredArgsConstructor
public class UsuarioRolMapper {

    private final RolMapper rolMapper;

    public List<RolResponse> toRolResponseList(List<Rol> roles) {
        return roles.stream()
                .map(rolMapper::toResponse)
                .toList();
    }

    public UsuarioRolResponse toResponse(UsuarioRol entity) {
        return UsuarioRolResponse.builder()
                .idUsuario(entity.getUsuario().getIdUsuario())
                .idRol(entity.getRol().getIdRol())
                .nombreRol(entity.getRol().getNombre())
                .fechaAsignacion(entity.getFechaAsignacion())
                .build();
    }
}

package sv.edu.ues.fmp.flora.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.entity.Rol;

@Component
@RequiredArgsConstructor
public class UsuarioRolMapper {

    private final RolMapper rolMapper;

    public List<RolResponse> toRolResponseList(List<Rol> roles) {
        return roles.stream()
                .map(rolMapper::toResponse)
                .toList();
    }
}

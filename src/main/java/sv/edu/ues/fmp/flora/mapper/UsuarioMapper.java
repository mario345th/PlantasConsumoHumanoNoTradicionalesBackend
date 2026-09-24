package sv.edu.ues.fmp.flora.mapper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCreationRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Usuario;

@Component
@RequiredArgsConstructor
public class UsuarioMapper {

    private final PasswordEncoder passwordEncoder;

    public Usuario toEntity(UsuarioCreationRequest request) {
        return Usuario.builder()
                .nombres(request.nombres())
                .apellidos(request.apellidos())
                .correo(request.correo())
                .nombreUsuario(request.nombreUsuario())
                .claveHash(passwordEncoder.encode(request.clave()))
                .build();
    }

    public void updateEntity(Usuario entity, UsuarioUpdateRequest request) {
        entity.setNombres(request.nombres());
        entity.setApellidos(request.apellidos());
        entity.setCorreo(request.correo());
        entity.setNombreUsuario(request.nombreUsuario());
    }

    public UsuarioResponse toResponse(Usuario entity) {
        return UsuarioResponse.builder()
                .id(entity.getIdUsuario())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .correo(entity.getCorreo())
                .nombreUsuario(entity.getNombreUsuario())
                .activo(entity.getActivo())
                .fechaRegistro(entity.getFechaRegistro())
                .fechaActualizacion(entity.getFechaActualizacion())
                .ultimoAcceso(entity.getUltimoAcceso())
                .build();
    }
}

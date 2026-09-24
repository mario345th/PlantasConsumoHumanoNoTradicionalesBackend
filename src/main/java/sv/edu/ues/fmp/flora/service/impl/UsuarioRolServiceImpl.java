package sv.edu.ues.fmp.flora.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioRolRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioRolResponse;
import sv.edu.ues.fmp.flora.entity.Permiso;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.UsuarioRol;
import sv.edu.ues.fmp.flora.entity.enums.UsuarioRolId;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.PermisoMapper;
import sv.edu.ues.fmp.flora.mapper.UsuarioRolMapper;
import sv.edu.ues.fmp.flora.repository.RolPermisoRepository;
import sv.edu.ues.fmp.flora.repository.RolRepository;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.repository.UsuarioRolRepository;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;

@Service
@RequiredArgsConstructor
public class UsuarioRolServiceImpl implements UsuarioRolService {

    private final UsuarioRolRepository usuarioRolRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final UsuarioRolMapper usuarioRolMapper;
    private final PermisoMapper permisoMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> obtenerRolesDeUsuario(Long idUsuario) {
        validarId(idUsuario, "usuario");
        if (!usuarioRepository.existsById(idUsuario)) {
            throw new RecursoNoEncontradoException("No existe un usuario con id " + idUsuario);
        }

        return usuarioRolMapper.toRolResponseList(usuarioRolRepository.findRolesByUsuarioId(idUsuario));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermisoResponse> obtenerPermisosEfectivosDeUsuario(Long idUsuario) {
        validarId(idUsuario, "usuario");
        if (!usuarioRepository.existsById(idUsuario)) {
            throw new RecursoNoEncontradoException("No existe un usuario con id " + idUsuario);
        }

        List<Rol> roles = usuarioRolRepository.findRolesByUsuarioId(idUsuario);

        Map<Long, Permiso> permisosPorId = new LinkedHashMap<>();
        for (Rol rol : roles) {
            for (Permiso permiso : rolPermisoRepository.findPermisosByRolId(rol.getIdRol())) {
                permisosPorId.putIfAbsent(permiso.getIdPermiso(), permiso);
            }
        }

        return permisosPorId.values().stream()
                .map(permisoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioRolResponse asignar(UsuarioRolRequest request) {
        Usuario usuario = usuarioRepository.findById(request.getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con id " + request.getIdUsuario()));

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un rol con id " + request.getIdRol()));

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new EstadoInvalidoException(
                    "No se puede asignar un rol al usuario " + usuario.getNombreUsuario()
                            + " porque está desactivado");
        }
        if (!Boolean.TRUE.equals(rol.getActivo())) {
            throw new EstadoInvalidoException(
                    "No se puede asignar el rol " + rol.getNombre() + " porque está desactivado");
        }

        UsuarioRolId id = new UsuarioRolId(usuario.getIdUsuario(), rol.getIdRol());
        if (usuarioRolRepository.existsById(id)) {
            throw new RecursoDuplicadoException(
                    "El usuario " + usuario.getNombreUsuario() + " ya tiene asignado el rol " + rol.getNombre());
        }

        UsuarioRol nuevo = UsuarioRol.builder()
                .id(id)
                .usuario(usuario)
                .rol(rol)
                .build();

        UsuarioRol guardado = usuarioRolRepository.save(nuevo);
        return usuarioRolMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public void quitar(Long idUsuario, Long idRol) {
        validarId(idUsuario, "usuario");
        validarId(idRol, "rol");

        UsuarioRolId id = new UsuarioRolId(idUsuario, idRol);
        if (!usuarioRolRepository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "El usuario " + idUsuario + " no tiene asignado el rol " + idRol);
        }

        usuarioRolRepository.deleteById(id);
    }

    private void validarId(Long id, String etiqueta) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del " + etiqueta + " debe ser un valor positivo");
        }
    }
}

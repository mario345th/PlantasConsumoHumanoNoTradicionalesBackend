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
import sv.edu.ues.fmp.flora.service.ProteccionDeAdministracion;
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

    /**
     * Las reglas de SEG-H09 sobre el rol ADMINISTRADOR. Viven en un componente
     * aparte porque la baja de usuario --{@code UsuarioServiceImpl.desactivar}--
     * aplica las mismas.
     */
    private final ProteccionDeAdministracion proteccionDeAdministracion;

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> obtenerRolesDeUsuario(Long idUsuario, boolean soloActivos) {
        validarId(idUsuario, "usuario");
        if (!usuarioRepository.existsById(idUsuario)) {
            throw new RecursoNoEncontradoException("No existe un usuario con id " + idUsuario);
        }

        List<Rol> roles = usuarioRolRepository.findRolesByUsuarioId(idUsuario);

        if (soloActivos) {
            roles = roles.stream()
                    .filter(rol -> Boolean.TRUE.equals(rol.getActivo()))
                    .toList();
        }

        return usuarioRolMapper.toRolResponseList(roles);
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
            // Un rol desactivado no otorga permisos: desactivarlo es
            // precisamente la forma de revocar lo que concedia. Sin este
            // filtro, dar de baja un rol no quitaba nada a quien ya lo tenia
            // asignado, porque la fila de usuario_rol sigue existiendo.
            if (!Boolean.TRUE.equals(rol.getActivo())) {
                continue;
            }

            for (Permiso permiso : rolPermisoRepository.findPermisosByRolId(rol.getIdRol())) {
                // Mismo criterio para el permiso en si: uno dado de baja no se
                // ejerce aunque lo conceda un rol activo.
                if (!Boolean.TRUE.equals(permiso.getActivo())) {
                    continue;
                }

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

        // SEG-H09 R2: solo el superadministrador asigna ADMINISTRADOR. Va aqui
        // --despues de resolver las dos entidades y antes de cualquier
        // comprobacion de estado o de duplicado-- porque es una decision de
        // autorizacion: a quien no puede hacer la operacion le toca el 403, no
        // un 409 sobre el estado del usuario o del rol. Para cualquier otro rol
        // no hace nada.
        proteccionDeAdministracion.verificarAsignacionDeRol(usuario, rol);

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

        // SEG-H09 R1, R3, R4 y R5. Las reglas necesitan las entidades y no los
        // ids: la marca es_superadmin del usuario y el nombre del rol. Las dos
        // filas existen por las claves foraneas de usuario_rol --si la fila de
        // la relacion existe, su usuario y su rol tambien-- asi que los
        // orElseThrow de abajo son defensivos y no un caso esperado.
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con id " + idUsuario));
        Rol rol = rolRepository.findById(idRol)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un rol con id " + idRol));

        proteccionDeAdministracion.verificarQuitarRol(usuario, rol);

        usuarioRolRepository.deleteById(id);
    }

    private void validarId(Long id, String etiqueta) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del " + etiqueta + " debe ser un valor positivo");
        }
    }
}

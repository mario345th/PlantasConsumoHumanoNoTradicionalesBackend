package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.RolPermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolPermisoResponse;
import sv.edu.ues.fmp.flora.entity.Permiso;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.RolPermiso;
import sv.edu.ues.fmp.flora.entity.enums.RolPermisoId;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.PermisoMapper;
import sv.edu.ues.fmp.flora.mapper.RolPermisoMapper;
import sv.edu.ues.fmp.flora.repository.PermisoRepository;
import sv.edu.ues.fmp.flora.repository.RolPermisoRepository;
import sv.edu.ues.fmp.flora.repository.RolRepository;
import sv.edu.ues.fmp.flora.service.RolPermisoService;

@Service
@RequiredArgsConstructor
public class RolPermisoServiceImpl implements RolPermisoService {

    private final RolPermisoRepository rolPermisoRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final PermisoMapper permisoMapper;
    private final RolPermisoMapper rolPermisoMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PermisoResponse> obtenerPermisosDeRol(Long idRol) {
        validarId(idRol, "rol");
        if (!rolRepository.existsById(idRol)) {
            throw new RecursoNoEncontradoException("No existe un rol con id " + idRol);
        }

        return rolPermisoRepository.findPermisosByRolId(idRol).stream()
                .map(permisoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public RolPermisoResponse asignar(RolPermisoRequest request) {
        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un rol con id " + request.getIdRol()));

        Permiso permiso = permisoRepository.findById(request.getIdPermiso())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un permiso con id " + request.getIdPermiso()));

        if (!Boolean.TRUE.equals(rol.getActivo())) {
            throw new EstadoInvalidoException(
                    "No se puede asignar un permiso al rol " + rol.getNombre() + " porque está desactivado");
        }
        if (!Boolean.TRUE.equals(permiso.getActivo())) {
            throw new EstadoInvalidoException(
                    "No se puede asignar el permiso " + permiso.getCodigo() + " porque está desactivado");
        }

        RolPermisoId id = new RolPermisoId(rol.getIdRol(), permiso.getIdPermiso());
        if (rolPermisoRepository.existsById(id)) {
            throw new RecursoDuplicadoException(
                    "El rol " + rol.getNombre() + " ya tiene asignado el permiso " + permiso.getCodigo());
        }

        RolPermiso nuevo = RolPermiso.builder()
                .id(id)
                .rol(rol)
                .permiso(permiso)
                .build();

        RolPermiso guardado = rolPermisoRepository.save(nuevo);
        return rolPermisoMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public void quitar(Long idRol, Long idPermiso) {
        validarId(idRol, "rol");
        validarId(idPermiso, "permiso");

        RolPermisoId id = new RolPermisoId(idRol, idPermiso);
        if (!rolPermisoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "El rol " + idRol + " no tiene asignado el permiso " + idPermiso);
        }

        rolPermisoRepository.deleteById(id);
    }

    private void validarId(Long id, String etiqueta) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del " + etiqueta + " debe ser un valor positivo");
        }
    }
}

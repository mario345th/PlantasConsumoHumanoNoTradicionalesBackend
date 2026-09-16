package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.UsuarioRolMapper;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.repository.UsuarioRolRepository;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;

@Service
@RequiredArgsConstructor
public class UsuarioRolServiceImpl implements UsuarioRolService {

    private final UsuarioRolRepository usuarioRolRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolMapper usuarioRolMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> obtenerRolesDeUsuario(Long idUsuario) {
        if (idUsuario == null || idUsuario <= 0) {
            throw new IdInvalidoException("El id del usuario debe ser un valor positivo");
        }
        if (!usuarioRepository.existsById(idUsuario)) {
            throw new RecursoNoEncontradoException("No existe un usuario con id " + idUsuario);
        }

        return usuarioRolMapper.toRolResponseList(usuarioRolRepository.findRolesByUsuarioId(idUsuario));
    }
}

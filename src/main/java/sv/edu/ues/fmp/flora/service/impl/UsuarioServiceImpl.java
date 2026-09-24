package sv.edu.ues.fmp.flora.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCambioClaveRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCreationRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.exception.CredencialesInvalidasException;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.UsuarioMapper;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.service.UsuarioService;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        List<UsuarioResponse> respuestas = new ArrayList<>();
        for (Usuario entidad : usuarioRepository.findAll()) {
            respuestas.add(usuarioMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarActivos() {
        List<UsuarioResponse> respuestas = new ArrayList<>();
        for (Usuario entidad : usuarioRepository.findByActivoTrue()) {
            respuestas.add(usuarioMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long id) {
        return usuarioMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public UsuarioResponse crear(UsuarioCreationRequest request) {
        if (usuarioRepository.existsByCorreoIgnoreCase(request.correo())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un usuario con el correo " + request.correo());
        }
        if (usuarioRepository.existsByNombreUsuarioIgnoreCase(request.nombreUsuario())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un usuario con el nombre de usuario " + request.nombreUsuario());
        }

        Usuario nuevo = usuarioMapper.toEntity(request);
        Usuario guardado = usuarioRepository.save(nuevo);

        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request) {
        Usuario entidad = buscarOFallar(id);

        usuarioRepository.findByCorreoIgnoreCase(request.correo())
                .filter(otro -> !otro.getIdUsuario().equals(id))
                .ifPresent(otro -> {
                    throw new RecursoDuplicadoException(
                            "Ya existe otro usuario con el correo " + request.correo());
                });

        usuarioRepository.findByNombreUsuarioIgnoreCase(request.nombreUsuario())
                .filter(otro -> !otro.getIdUsuario().equals(id))
                .ifPresent(otro -> {
                    throw new RecursoDuplicadoException(
                            "Ya existe otro usuario con el nombre de usuario " + request.nombreUsuario());
                });

        usuarioMapper.updateEntity(entidad, request);

        Usuario actualizado = usuarioRepository.saveAndFlush(entidad);

        return usuarioMapper.toResponse(actualizado);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Usuario entidad = buscarOFallar(id);
        entidad.setActivo(false);
    }

    @Override
    @Transactional
    public UsuarioResponse reactivar(Long id) {
        Usuario entidad = buscarOFallar(id);
        entidad.setActivo(true);
        Usuario actualizado = usuarioRepository.saveAndFlush(entidad);
        return usuarioMapper.toResponse(actualizado);
    }

    @Override
    @Transactional
    public void cambiarClave(Long id, UsuarioCambioClaveRequest request) {
        Usuario entidad = buscarOFallar(id);

        if (!passwordEncoder.matches(request.claveActual(), entidad.getClaveHash())) {
            throw new CredencialesInvalidasException("La clave actual no es correcta");
        }

        entidad.setClaveHash(passwordEncoder.encode(request.claveNueva()));
        usuarioRepository.saveAndFlush(entidad);
    }

    @Override
    @Transactional
    public UsuarioResponse iniciarSesion(UsuarioLoginRequest request) {
        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCaseOrNombreUsuarioIgnoreCase(request.usuario(), request.usuario())
                .filter(Usuario::getActivo)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario o clave incorrectos"));

        if (!passwordEncoder.matches(request.clave(), usuario.getClaveHash())) {
            throw new CredencialesInvalidasException("Usuario o clave incorrectos");
        }

        usuario.setUltimoAcceso(LocalDateTime.now());
        Usuario actualizado = usuarioRepository.saveAndFlush(usuario);

        return usuarioMapper.toResponse(actualizado);
    }

    private Usuario buscarOFallar(Long id) {
        validarId(id);
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con id " + id));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del usuario debe ser un valor positivo");
        }
    }
}

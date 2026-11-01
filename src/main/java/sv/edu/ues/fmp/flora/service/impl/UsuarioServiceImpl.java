package sv.edu.ues.fmp.flora.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCambioClaveRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioCreationRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioLoginRequest;
import sv.edu.ues.fmp.flora.dto.request.UsuarioUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.PaginaResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.exception.CredencialesInvalidasException;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.UsuarioMapper;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.service.ControlDeIntentosDeLogin;
import sv.edu.ues.fmp.flora.service.UsuarioService;
import sv.edu.ues.fmp.flora.util.Paginacion;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * El freno a la fuerza bruta se aplica aqui, en el metodo compartido, y no
     * en cada controlador: asi cubre de una vez {@code POST /api/auth/login} y
     * el viejo {@code POST /api/usuarios/login}, que los dos pasan por
     * {@link #iniciarSesion(UsuarioLoginRequest)}.
     */
    private final ControlDeIntentosDeLogin controlDeIntentosDeLogin;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listarTodos(int pagina, int tamanio) {
        Pageable pageable = Paginacion.armar(pagina, tamanio, "idUsuario");
        return Paginacion.aRespuesta(usuarioRepository.findAll(pageable), usuarioMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listarActivos(int pagina, int tamanio) {
        Pageable pageable = Paginacion.armar(pagina, tamanio, "idUsuario");
        return Paginacion.aRespuesta(usuarioRepository.findByActivoTrue(pageable), usuarioMapper::toResponse);
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
        // Lo primero, antes de tocar la base: durante el enfriamiento la
        // respuesta tiene que ser la misma con la clave correcta y con una
        // equivocada, o el bloqueo seguiria sirviendo para probar claves.
        controlDeIntentosDeLogin.verificarQuePuedeIntentar(request.usuario());

        Optional<Usuario> encontrado = usuarioRepository
                .findByCorreoIgnoreCaseOrNombreUsuarioIgnoreCase(request.usuario(), request.usuario())
                .filter(Usuario::getActivo);

        // Las tres causas de fallo --no existe, esta desactivado, la clave no
        // coincide-- se tratan juntas para que sigan siendo indistinguibles
        // desde fuera y para que las tres cuenten igual contra el limite.
        if (encontrado.isEmpty()
                || !passwordEncoder.matches(request.clave(), encontrado.get().getClaveHash())) {

            // El contador vive en memoria, no en la transaccion: el rollback
            // que se lleva esta excepcion no lo deshace, que es justo lo que
            // hace falta para que el fallo quede contado.
            controlDeIntentosDeLogin.registrarFallo(request.usuario());

            throw new CredencialesInvalidasException("Usuario o clave incorrectos");
        }

        controlDeIntentosDeLogin.registrarExito(request.usuario());

        Usuario usuario = encontrado.get();
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

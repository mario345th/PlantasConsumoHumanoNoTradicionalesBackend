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
import sv.edu.ues.fmp.flora.service.ProteccionDeAdministracion;
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

    /**
     * Las reglas de SEG-H09 que protegen el acceso de administracion. Las
     * mismas que aplica {@code UsuarioRolServiceImpl} al asignar o quitar el
     * rol ADMINISTRADOR; aqui se usan para la baja logica.
     */
    private final ProteccionDeAdministracion proteccionDeAdministracion;

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

        // SEG-H09. La baja logica es la otra via por la que se podia perder el
        // acceso de administracion: nadie se desactiva a si mismo (R1), al
        // superadministrador no lo desactiva nadie (R4), a un administrador
        // solo lo desactiva el superadministrador (R3) y ninguna baja puede
        // dejar el sistema sin administradores activos (R5).
        //
        // Va despues de buscarOFallar para que un id inexistente siga siendo
        // 404 y no un 403: las reglas hablan de un usuario concreto y no tiene
        // sentido evaluarlas sobre uno que no existe.
        proteccionDeAdministracion.verificarDesactivacion(entidad);

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
        // Lo primero es RESOLVER LA CUENTA, y por eso esta consulta va antes
        // del freno (SEG-H07). El campo usuario acepta el correo o el nombre
        // de usuario, asi que el contador tiene que ir por id_usuario: cuando
        // iba por el texto escrito, agotar los intentos con el nombre de
        // usuario dejaba otros cinco disponibles escribiendo el correo de la
        // misma cuenta, y el bloqueo se esquivaba alternandolos.
        //
        // No se filtra por activo aqui: una cuenta desactivada tambien es una
        // cuenta, y sus intentos tienen que contar contra su propio id. El
        // estado se exige mas abajo, junto con la clave.
        Optional<Usuario> encontrado = usuarioRepository
                .findByCorreoIgnoreCaseOrNombreUsuarioIgnoreCase(request.usuario(), request.usuario());

        ControlDeIntentosDeLogin.Cuenta cuenta = encontrado
                .map(usuario -> ControlDeIntentosDeLogin.Cuenta.deUsuario(usuario.getIdUsuario()))
                // Un texto que no corresponde a ninguna cuenta no tiene id;
                // se cuenta por el texto para que probar usuarios inventados
                // tampoco sea ilimitado.
                .orElseGet(() -> ControlDeIntentosDeLogin.Cuenta.noResuelta(request.usuario()));

        // Antes de comparar la clave: durante el enfriamiento la respuesta
        // tiene que ser la misma con la clave correcta y con una equivocada, o
        // el bloqueo seguiria sirviendo para probar claves. La comparacion
        // BCrypt, que es la parte cara y la unica que podria delatar algo por
        // tiempos, nunca se llega a ejecutar.
        controlDeIntentosDeLogin.verificarQuePuedeIntentar(cuenta);

        Optional<Usuario> valido = encontrado.filter(Usuario::getActivo);

        // Las tres causas de fallo --no existe, esta desactivado, la clave no
        // coincide-- se tratan juntas para que sigan siendo indistinguibles
        // desde fuera y para que las tres cuenten igual contra el limite.
        if (valido.isEmpty()
                || !passwordEncoder.matches(request.clave(), valido.get().getClaveHash())) {

            // El contador vive en memoria, no en la transaccion: el rollback
            // que se lleva esta excepcion no lo deshace, que es justo lo que
            // hace falta para que el fallo quede contado.
            controlDeIntentosDeLogin.registrarFallo(cuenta);

            throw new CredencialesInvalidasException("Usuario o clave incorrectos");
        }

        controlDeIntentosDeLogin.registrarExito(cuenta);

        Usuario usuario = valido.get();
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

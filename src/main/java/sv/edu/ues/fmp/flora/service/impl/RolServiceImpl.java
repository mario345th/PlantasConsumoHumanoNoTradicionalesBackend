package sv.edu.ues.fmp.flora.service.impl;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.RolRequest;
import sv.edu.ues.fmp.flora.dto.response.PaginaResponse;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.RolMapper;
import sv.edu.ues.fmp.flora.repository.RolRepository;
import sv.edu.ues.fmp.flora.service.ProteccionDeAdministracion;
import sv.edu.ues.fmp.flora.service.RolService;
import sv.edu.ues.fmp.flora.util.Paginacion;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;
    private final RolMapper rolMapper;

    /**
     * El invariante de SEG-H09 sobre el rol ADMINISTRADOR. Aqui solo se le
     * pregunta por la baja del rol: el catalogo de roles no tiene usuario
     * objetivo, asi que las reglas de autoproteccion y de "quien llama" no
     * entran. Ver {@code ProteccionDeAdministracion.verificarDesactivacionDeRol}.
     */
    private final ProteccionDeAdministracion proteccionDeAdministracion;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<RolResponse> listarTodos(int pagina, int tamanio) {
        Pageable pageable = Paginacion.armar(pagina, tamanio, "idRol");
        return Paginacion.aRespuesta(rolRepository.findAll(pageable), rolMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<RolResponse> listarActivos(int pagina, int tamanio) {
        Pageable pageable = Paginacion.armar(pagina, tamanio, "idRol");
        return Paginacion.aRespuesta(rolRepository.findByActivoTrue(pageable), rolMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RolResponse obtenerPorId(Long id) {
        return rolMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public RolResponse crear(RolRequest request) {
        if (rolRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un rol con el nombre " + request.getNombre());
        }

        Rol nuevo = rolMapper.toEntity(request);
        Rol guardado = rolRepository.save(nuevo);

        return rolMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public RolResponse actualizar(Long id, RolRequest request) {
        Rol entidad = buscarOFallar(id);

        Optional<Rol> conMismoNombre = rolRepository.findByNombreIgnoreCase(request.getNombre());
        if (conMismoNombre.isPresent() && !conMismoNombre.get().getIdRol().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otro rol con el nombre " + request.getNombre());
        }

        rolMapper.updateEntity(entidad, request);
        return rolMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Rol entidad = buscarOFallar(id);

        // SEG-H09 R5: dar de baja el rol ADMINISTRADOR lo deja sin efecto para
        // todos los que lo tienen asignado, asi que es otra via --la sexta-- a
        // un sistema sin administradores activos. Va despues de resolver la
        // entidad: un id que no existe sigue siendo 404, igual que en la baja
        // de usuario. Para cualquier otro rol no hace nada.
        proteccionDeAdministracion.verificarDesactivacionDeRol(entidad);

        entidad.setActivo(false);
    }

    @Override
    @Transactional
    public RolResponse reactivar(Long id) {
        Rol entidad = buscarOFallar(id);
        entidad.setActivo(true);
        return rolMapper.toResponse(entidad);
    }

    private Rol buscarOFallar(Long id) {
        validarId(id);
        return rolRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un rol con id " + id));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del rol debe ser un valor positivo");
        }
    }
}

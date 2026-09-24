package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.PermisoRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.entity.Permiso;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.PermisoMapper;
import sv.edu.ues.fmp.flora.repository.PermisoRepository;
import sv.edu.ues.fmp.flora.service.PermisoService;

@Service
@RequiredArgsConstructor
public class PermisoServiceImpl implements PermisoService {

    private final PermisoRepository permisoRepository;
    private final PermisoMapper permisoMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PermisoResponse> listarTodos() {
        List<PermisoResponse> respuestas = new ArrayList<>();
        for (Permiso entidad : permisoRepository.findAll()) {
            respuestas.add(permisoMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermisoResponse> listarActivos() {
        List<PermisoResponse> respuestas = new ArrayList<>();
        for (Permiso entidad : permisoRepository.findByActivoTrue()) {
            respuestas.add(permisoMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public PermisoResponse obtenerPorId(Long id) {
        return permisoMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public PermisoResponse crear(PermisoRequest request) {
        if (permisoRepository.existsByCodigoIgnoreCase(request.getCodigo())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un permiso con el código " + request.getCodigo());
        }

        Permiso nuevo = permisoMapper.toEntity(request);
        Permiso guardado = permisoRepository.save(nuevo);

        return permisoMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public PermisoResponse actualizar(Long id, PermisoRequest request) {
        Permiso entidad = buscarOFallar(id);

        Optional<Permiso> conMismoCodigo = permisoRepository.findByCodigoIgnoreCase(request.getCodigo());
        if (conMismoCodigo.isPresent() && !conMismoCodigo.get().getIdPermiso().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otro permiso con el código " + request.getCodigo());
        }

        permisoMapper.updateEntity(entidad, request);
        return permisoMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Permiso entidad = buscarOFallar(id);
        entidad.setActivo(false);
    }

    @Override
    @Transactional
    public PermisoResponse reactivar(Long id) {
        Permiso entidad = buscarOFallar(id);
        entidad.setActivo(true);
        return permisoMapper.toResponse(entidad);
    }

    private Permiso buscarOFallar(Long id) {
        validarId(id);
        return permisoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un permiso con id " + id));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id del permiso debe ser un valor positivo");
        }
    }
}

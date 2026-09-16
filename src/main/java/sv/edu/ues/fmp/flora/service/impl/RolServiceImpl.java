package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.RolRequest;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.RolMapper;
import sv.edu.ues.fmp.flora.repository.RolRepository;
import sv.edu.ues.fmp.flora.service.RolService;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;
    private final RolMapper rolMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> listarTodos() {
        List<RolResponse> respuestas = new ArrayList<>();
        for (Rol entidad : rolRepository.findAll()) {
            respuestas.add(rolMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> listarActivos() {
        List<RolResponse> respuestas = new ArrayList<>();
        for (Rol entidad : rolRepository.findByActivoTrue()) {
            respuestas.add(rolMapper.toResponse(entidad));
        }
        return respuestas;
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
        entidad.setActivo(false);
    }

    private Rol buscarOFallar(Long id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un rol con id " + id));
    }
}

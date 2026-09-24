package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.DepartamentoRequest;
import sv.edu.ues.fmp.flora.dto.response.DepartamentoResponse;
import sv.edu.ues.fmp.flora.entity.Departamento;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.DepartamentoMapper;
import sv.edu.ues.fmp.flora.repository.DepartamentoRepository;
import sv.edu.ues.fmp.flora.service.DepartamentoService;

@Service
@RequiredArgsConstructor
public class DepartamentoServiceImpl implements DepartamentoService {

    /** Acceso a las operaciones de persistencia de Departamento. */
    private final DepartamentoRepository departamentoRepository;
    /** Conversión entre entidades y DTOs, sin lógica de negocio. */
    private final DepartamentoMapper departamentoMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DepartamentoResponse> listarTodos() {
        // Convierte cada entidad de la base al formato de respuesta.
        return departamentoRepository.findAll().stream()
                .map(departamentoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DepartamentoResponse obtenerPorId(Long id) {
        return departamentoMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public DepartamentoResponse crear(DepartamentoRequest request) {
        // Se anticipa la restricción UNIQUE de la base para devolver un 409 claro.
        if (departamentoRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un departamento con el nombre " + request.getNombre());
        }

        Departamento guardado = departamentoRepository.save(departamentoMapper.toEntity(request));
        return departamentoMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public DepartamentoResponse actualizar(Long id, DepartamentoRequest request) {
        Departamento entidad = buscarOFallar(id);
        Optional<Departamento> conMismoNombre =
                departamentoRepository.findByNombreIgnoreCase(request.getNombre());

        // El mismo nombre es válido para el registro actual, pero no para otro.
        if (conMismoNombre.isPresent()
                && !conMismoNombre.get().getIdDepartamento().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otro departamento con el nombre " + request.getNombre());
        }

        departamentoMapper.updateEntity(entidad, request);
        return departamentoMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        // No se elimina físicamente porque Municipio depende de Departamento.
        buscarOFallar(id).setActivo(false);
    }

    private Departamento buscarOFallar(Long id) {
        // Centraliza el 404 utilizado por todas las operaciones que requieren el registro.
        return departamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un departamento con id " + id));
    }
}

package sv.edu.ues.fmp.flora.service.impl;



import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sv.edu.ues.fmp.flora.dto.request.MunicipioRequest;
import sv.edu.ues.fmp.flora.dto.request.MunicipioResponse;
import sv.edu.ues.fmp.flora.entity.Departamento;
import sv.edu.ues.fmp.flora.entity.Municipio;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.MunicipioMapper;
import sv.edu.ues.fmp.flora.repository.DepartamentoRepository;
import sv.edu.ues.fmp.flora.repository.MunicipioRepository;
import sv.edu.ues.fmp.flora.service.MunicipioService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MunicipioServiceImpl implements MunicipioService {

    private final MunicipioRepository municipioRepository;
    // Un Crud dependiente utiliza un segundo repositorio
    private final DepartamentoRepository departamentoRepository;
    private final MunicipioMapper municipioMapper;

    @Override
    @Transactional(readOnly = true)
    public List<MunicipioResponse> listarTodos() {
        return municipioRepository.findAll().stream().map(municipioMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MunicipioResponse> listarPorDepartamento(Long idDepartamento) {
        if (!departamentoRepository.existsById(idDepartamento)) {
            throw new RecursoNoEncontradoException(
                    "No existe el departamento con id " + idDepartamento);
        }
        return municipioRepository.findByDepartamentoIdDepartamento(idDepartamento)
                .stream().map(municipioMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MunicipioResponse> buscarPorNombre(String nombre) {
        // Un termino vacio devuelve la lista vacia y no los 260 municipios: el
        // LIKE con comodines a ambos lados casaria con todas las filas, que no
        // es lo que espera quien vacio la caja de busqueda.
        if (nombre == null || nombre.isBlank()) {
            return List.of();
        }
        return municipioRepository.findByNombreContainingIgnoreCase(nombre.trim())
                .stream().map(municipioMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MunicipioResponse obtenerPorId(Long id) {
        return municipioMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public MunicipioResponse crear(MunicipioRequest request) {
        //El padre tiene que existir
        Departamento departamento = buscarDepartamentoOFallar(request.getIdDepartamento());

        // Duplicado DENTRO de ese departamento, no global
        if (municipioRepository.existsByDepartamentoIdDepartamentoAndNombreIgnoreCase(
                departamento.getIdDepartamento(), request.getNombre())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un municipio llamado '" + request.getNombre()
                            + "' en el departamento " + departamento.getNombre());
        }

        return municipioMapper.toResponse(municipioRepository.save(municipioMapper.toEntity(request, departamento)));
    }

    @Override
    @Transactional
    public MunicipioResponse actualizar(Long id, MunicipioRequest request) {
        Municipio entidad = buscarOFallar(id);
        Departamento departamento = buscarDepartamentoOFallar(request.getIdDepartamento());

        municipioRepository.findByDepartamentoIdDepartamentoAndNombreIgnoreCase(
                        departamento.getIdDepartamento(), request.getNombre())
                .filter(otro -> !otro.getIdMunicipio().equals(id))
                .ifPresent(otro -> {
                    throw new RecursoDuplicadoException(
                            "Ya existe otro municipio con ese nombre en " + departamento.getNombre());
                });

        municipioMapper.updateEntity(entidad, request, departamento);
        return municipioMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        buscarOFallar(id).setActivo(false);
    }

    private Municipio buscarOFallar(Long id) {
        return municipioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el municipio con id " + id));
    }

    private Departamento buscarDepartamentoOFallar(Long id) {
        return departamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el departamento con id " + id));
    }
}
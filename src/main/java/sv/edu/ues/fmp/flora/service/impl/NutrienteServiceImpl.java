package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.NutrienteRequest;
import sv.edu.ues.fmp.flora.dto.response.NutrienteResponse;
import sv.edu.ues.fmp.flora.entity.Nutriente;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.NutrienteMapper;
import sv.edu.ues.fmp.flora.repository.NutrienteRepository;
import sv.edu.ues.fmp.flora.service.NutrienteService;

@Service
@RequiredArgsConstructor
public class NutrienteServiceImpl implements NutrienteService {

    private final NutrienteRepository nutrienteRepository;
    private final NutrienteMapper nutrienteMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NutrienteResponse> listarTodas() {
        List<NutrienteResponse> respuestas = new ArrayList<>();
        for (Nutriente entidad : nutrienteRepository.findAllByOrderByIdNutrienteAsc()) {
            respuestas.add(nutrienteMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutrienteResponse> listarActivas() {
        List<NutrienteResponse> respuestas = new ArrayList<>();
        for (Nutriente entidad : nutrienteRepository.findByActivoTrueOrderByIdNutrienteAsc()) {
            respuestas.add(nutrienteMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public NutrienteResponse obtenerPorId(Long id) {
        Nutriente entidad = buscarOFallar(id);
        return nutrienteMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public NutrienteResponse crear(NutrienteRequest request) {
        Optional<Nutriente> existente = nutrienteRepository.findByNombreIgnoreCase(request.getNombre());

        if (existente.isPresent()) {
            if (existente.get().getActivo()) {
                throw new RecursoDuplicadoException(
                        "Ya existe un nutriente con el nombre " + request.getNombre());
            } else {
                throw new RecursoDuplicadoException(
                        "Ya existe el nutriente '" + request.getNombre() + "', pero está desactivado. Actívelo en vez de crear uno nuevo.");
            }
        }

        Nutriente nueva = nutrienteMapper.toEntity(request);
        Nutriente guardada = nutrienteRepository.save(nueva);
        return nutrienteMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public NutrienteResponse actualizar(Long id, NutrienteRequest request) {
        Nutriente entidad = buscarOFallar(id);

        Optional<Nutriente> conMismoNombre =
                nutrienteRepository.findByNombreIgnoreCase(request.getNombre());

        if (conMismoNombre.isPresent() && !conMismoNombre.get().getIdNutriente().equals(id)) {
            if (conMismoNombre.get().getActivo()) {
                throw new RecursoDuplicadoException(
                        "Ya existe otro nutriente con el nombre " + request.getNombre());
            } else {
                throw new RecursoDuplicadoException(
                        "Ya existe el nutriente '" + request.getNombre() + "', pero está desactivado. Actívelo en vez de ocupar su nombre.");
            }
        }

        nutrienteMapper.updateEntity(entidad, request);
        return nutrienteMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Nutriente entidad = buscarOFallar(id);
        entidad.setActivo(false);
    }

    private Nutriente buscarOFallar(Long id) {
        return nutrienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un nutriente con id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutrienteResponse> buscarPorNombre(String palabraClave) {
        List<NutrienteResponse> respuestas = new ArrayList<>();
        for (Nutriente entidad : nutrienteRepository.findByNombreContainingIgnoreCaseAndActivoTrue(palabraClave)) {
            respuestas.add(nutrienteMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutrienteResponse> buscarPorCategoria(CategoriaNutriente categoria) {
        List<NutrienteResponse> respuestas = new ArrayList<>();
        for (Nutriente entidad : nutrienteRepository.findByCategoriaAndActivoTrueOrderByIdNutrienteAsc(categoria)) {
            respuestas.add(nutrienteMapper.toResponse(entidad));
        }
        return respuestas;
    }
}
package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.PartePlantaRequest;
import sv.edu.ues.fmp.flora.dto.response.PartePlantaResponse;
import sv.edu.ues.fmp.flora.entity.PartePlanta;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.PartePlantaMapper;
import sv.edu.ues.fmp.flora.repository.PartePlantaRepository;
import sv.edu.ues.fmp.flora.service.PartePlantaService;

/**
 * Implementacion de la logica de negocio de las partes de planta.
 * Aqui viven las reglas (unicidad del nombre, baja logica) y aqui termina el
 * recorrido de la entidad: hacia arriba solo salen DTOs.
 */
@Service
@RequiredArgsConstructor
public class PartePlantaServiceImpl implements PartePlantaService {

    private final PartePlantaRepository partePlantaRepository;
    private final PartePlantaMapper partePlantaMapper;


    @Override
    @Transactional(readOnly = true)
    public List<PartePlantaResponse> listarTodas() {
        List<PartePlantaResponse> respuestas = new ArrayList<>();
        for (PartePlanta entidad : partePlantaRepository.findAll()) {
            respuestas.add(partePlantaMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartePlantaResponse> listarActivas() {
        List<PartePlantaResponse> respuestas = new ArrayList<>();
        for (PartePlanta entidad : partePlantaRepository.findByActivoTrue()) {
            respuestas.add(partePlantaMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartePlantaResponse> buscarPorNombre(String nombre) {
        // Un termino vacio devuelve la lista vacia y no el catalogo entero: el
        // LIKE con comodines a ambos lados casaria con todas las filas, que no
        // es lo que espera quien vacio la caja de busqueda.
        if (nombre == null || nombre.isBlank()) {
            return List.of();
        }

        List<PartePlantaResponse> respuestas = new ArrayList<>();
        for (PartePlanta entidad : partePlantaRepository.findByNombreContainingIgnoreCase(nombre.trim())) {
            respuestas.add(partePlantaMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public PartePlantaResponse obtenerPorId(Long id) {
        PartePlanta entidad = buscarOFallar(id);
        return partePlantaMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public PartePlantaResponse crear(PartePlantaRequest request) {
        if (partePlantaRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new RecursoDuplicadoException(
                    "Ya existe una parte de planta con el nombre " + request.getNombre());
        }

        PartePlanta nueva = partePlantaMapper.toEntity(request);

        PartePlanta guardada = partePlantaRepository.save(nueva);

        return partePlantaMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public PartePlantaResponse actualizar(Long id, PartePlantaRequest request) {
        PartePlanta entidad = buscarOFallar(id);

        Optional<PartePlanta> conMismoNombre =
                partePlantaRepository.findByNombreIgnoreCase(request.getNombre());
        if (conMismoNombre.isPresent()
                && !conMismoNombre.get().getIdPartePlanta().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otra parte de planta con el nombre " + request.getNombre());
        }

        partePlantaMapper.updateEntity(entidad, request);

        return partePlantaMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        PartePlanta entidad = buscarOFallar(id);

        entidad.setActivo(false);

    }



    private PartePlanta buscarOFallar(Long id) {
        return partePlantaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una parte de planta con id " + id));
    }
}

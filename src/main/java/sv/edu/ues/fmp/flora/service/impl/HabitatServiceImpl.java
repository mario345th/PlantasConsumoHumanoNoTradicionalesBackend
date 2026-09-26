package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.HabitatRequest;
import sv.edu.ues.fmp.flora.dto.response.HabitatResponse;
import sv.edu.ues.fmp.flora.entity.Habitat;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.HabitatMapper;
import sv.edu.ues.fmp.flora.repository.HabitatRepository;
import sv.edu.ues.fmp.flora.service.HabitatService;

@Service
@RequiredArgsConstructor
public class HabitatServiceImpl implements HabitatService {

    private final HabitatRepository habitatRepository;
    private final HabitatMapper habitatMapper;

    @Override
    @Transactional(readOnly = true)
    public List<HabitatResponse> listarTodos() {
        List<HabitatResponse> respuestas = new ArrayList<>();
        for (Habitat entidad : habitatRepository.findAll()) {
            respuestas.add(habitatMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HabitatResponse> listarActivos() {
        List<HabitatResponse> respuestas = new ArrayList<>();
        for (Habitat entidad : habitatRepository.findByActivoTrue()) {
            respuestas.add(habitatMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HabitatResponse> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El parámetro de búsqueda 'nombre' es obligatorio y no puede estar vacío"
            );
        }

        List<HabitatResponse> respuestas = new ArrayList<>();
        for (Habitat entidad : habitatRepository.findByNombreContainingIgnoreCase(nombre.trim())) {
            respuestas.add(habitatMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public HabitatResponse obtenerPorId(Long id) {
        return habitatMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public HabitatResponse crear(HabitatRequest request) {
        Optional<Habitat> existente = habitatRepository.findByNombreIgnoreCase(request.getNombre().trim());
        if (existente.isPresent()) {
            Habitat h = existente.get();
            if (Boolean.FALSE.equals(h.getActivo())) {
                // Si el hábitat ya existe pero estaba inactivo, lo reactivamos automáticamente
                h.setActivo(true);
                if (request.getDescripcion() != null) {
                    h.setDescripcion(request.getDescripcion());
                }
                Habitat guardado = habitatRepository.save(h);
                return habitatMapper.toResponse(guardado);
            }
            throw new RecursoDuplicadoException(
                    "Ya existe un hábitat con el nombre " + request.getNombre().trim());
        }

        Habitat nuevo = habitatMapper.toEntity(request);
        if (nuevo.getActivo() == null) {
            nuevo.setActivo(true);
        }
        Habitat guardado = habitatRepository.save(nuevo);

        return habitatMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public HabitatResponse actualizar(Long id, HabitatRequest request) {
        Habitat entidad = buscarOFallar(id);

        Optional<Habitat> conMismoNombre = habitatRepository.findByNombreIgnoreCase(request.getNombre().trim());
        if (conMismoNombre.isPresent() && !conMismoNombre.get().getIdHabitat().equals(id)) {
            Habitat h = conMismoNombre.get();
            if (Boolean.FALSE.equals(h.getActivo())) {
                throw new RecursoDuplicadoException(
                        "El hábitat '" + request.getNombre().trim() + "' ya existe, pero está desactivado");
            }
            throw new RecursoDuplicadoException(
                    "Ya existe otro hábitat con el nombre " + request.getNombre().trim());
        }

        habitatMapper.updateEntity(entidad, request);
        return habitatMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Habitat entidad = buscarOFallar(id);
        entidad.setActivo(false);
    }

    private Habitat buscarOFallar(Long id) {
        return habitatRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un hábitat con id " + id));
    }
}
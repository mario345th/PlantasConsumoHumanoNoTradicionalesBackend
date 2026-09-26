package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.FuenteRequest;
import sv.edu.ues.fmp.flora.dto.response.FuenteResponse;
import sv.edu.ues.fmp.flora.entity.Fuente;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.FuenteMapper;
import sv.edu.ues.fmp.flora.repository.FuenteRepository;
import sv.edu.ues.fmp.flora.service.FuenteService;

@Service
@RequiredArgsConstructor
public class FuenteServiceImpl implements FuenteService {

    private final FuenteRepository fuenteRepository;
    private final FuenteMapper fuenteMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FuenteResponse> listarTodas() {
        List<FuenteResponse> respuestas = new ArrayList<>();
        for (Fuente entidad : fuenteRepository.findAll()) {
            respuestas.add(fuenteMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FuenteResponse> listarActivas() {
        List<FuenteResponse> respuestas = new ArrayList<>();
        for (Fuente entidad : fuenteRepository.findByActivaTrue()) {
            respuestas.add(fuenteMapper.toResponse(entidad));
        }
        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public FuenteResponse obtenerPorId(Long id) {
        Fuente entidad = buscarOFallar(id);
        return fuenteMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public FuenteResponse crear(FuenteRequest request) {
        normalizar(request);

        if (fuenteRepository.existsByTituloIgnoreCase(request.getTitulo())) {
            throw new RecursoDuplicadoException(
                    "Ya existe una fuente con el título " + request.getTitulo());
        }

        Fuente nueva = fuenteMapper.toEntity(request);

        Fuente guardada = fuenteRepository.save(nueva);

        return fuenteMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public FuenteResponse actualizar(Long id, FuenteRequest request) {
        normalizar(request);

        Fuente entidad = buscarOFallar(id);

        Optional<Fuente> conMismoTitulo =
                fuenteRepository.findByTituloIgnoreCase(request.getTitulo());
        if (conMismoTitulo.isPresent()
                && !conMismoTitulo.get().getIdFuente().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otra fuente con el título " + request.getTitulo());
        }

        fuenteMapper.updateEntity(entidad, request);

        return fuenteMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Fuente entidad = buscarOFallar(id);

        entidad.setActiva(false);

    }

    private Fuente buscarOFallar(Long id) {
        return fuenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una fuente con id " + id));
    }


    private void normalizar(FuenteRequest request) {
        request.setTitulo(limpiarEspacios(request.getTitulo()));
        request.setUrl(limpiarEspacios(request.getUrl()));
    }

    private String limpiarEspacios(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }
}
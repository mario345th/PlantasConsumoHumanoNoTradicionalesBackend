package sv.edu.ues.fmp.flora.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sv.edu.ues.fmp.flora.dto.request.BeneficioRequest;
import sv.edu.ues.fmp.flora.dto.response.BeneficioResponse;
import sv.edu.ues.fmp.flora.entity.Beneficio;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.BeneficioMapper;
import sv.edu.ues.fmp.flora.repository.BeneficioRepository;
import sv.edu.ues.fmp.flora.service.BeneficioService;

import java.util.List;

/**
 * Implementacion del servicio para la gestion de beneficios.
 */
@Service
@RequiredArgsConstructor
public class BeneficioServiceImpl implements BeneficioService {

    private final BeneficioRepository beneficioRepository;
    private final BeneficioMapper beneficioMapper;

    /**
     * Lista todos los beneficios registrados.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BeneficioResponse> listarTodos() {

        return beneficioRepository.findAll()
                .stream()
                .map(beneficioMapper::toResponse)
                .toList();
    }

    /**
     * Obtiene un beneficio por su ID.
     */
    @Override
    @Transactional(readOnly = true)
    public BeneficioResponse obtenerPorId(Long id) {

        Beneficio beneficio = buscarPorId(id);

        return beneficioMapper.toResponse(beneficio);
    }

    /**
     * Registra un nuevo beneficio.
     */
    @Override
    @Transactional
    public BeneficioResponse crear(BeneficioRequest request) {

        Beneficio beneficio =
                beneficioMapper.toEntity(request);

        Beneficio guardado =
                beneficioRepository.save(beneficio);

        return beneficioMapper.toResponse(guardado);
    }

    /**
     * Actualiza un beneficio existente.
     */
    @Override
    @Transactional
    public BeneficioResponse actualizar(
            Long id,
            BeneficioRequest request
    ) {

        Beneficio beneficio = buscarPorId(id);

        beneficioMapper.updateEntity(
                beneficio,
                request
        );

        Beneficio actualizado =
                beneficioRepository.save(beneficio);

        return beneficioMapper.toResponse(actualizado);
    }

    /**
     * Elimina un beneficio.
     */
    @Override
    @Transactional
    public void eliminar(Long id) {

        Beneficio beneficio = buscarPorId(id);

        beneficioRepository.delete(beneficio);
    }

    /**
     * Busca internamente un beneficio.
     *
     * @param id identificador del beneficio.
     * @return entidad encontrada.
     * @throws RecursoNoEncontradoException si el beneficio no existe.
     */
    private Beneficio buscarPorId(Long id) {

        return beneficioRepository
                .findById(id)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(
                                "No existe un beneficio con id: " + id
                        )
                );
    }
}
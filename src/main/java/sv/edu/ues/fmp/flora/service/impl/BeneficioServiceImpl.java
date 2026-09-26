package sv.edu.ues.fmp.flora.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sv.edu.ues.fmp.flora.dto.request.BeneficioRequest;
import sv.edu.ues.fmp.flora.dto.response.BeneficioResponse;
import sv.edu.ues.fmp.flora.entity.Beneficio;
import sv.edu.ues.fmp.flora.exception.DatoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.BeneficioMapper;
import sv.edu.ues.fmp.flora.repository.BeneficioRepository;
import sv.edu.ues.fmp.flora.service.BeneficioService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BeneficioServiceImpl implements BeneficioService {

    private final BeneficioRepository beneficioRepository;
    private final BeneficioMapper beneficioMapper;

    @Override
    @Transactional(readOnly = true)
    public List<BeneficioResponse> listarTodos() {

        return beneficioRepository.findAll()
                .stream()
                .map(beneficioMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficioResponse obtenerPorId(Long id) {

        Beneficio beneficio = buscarPorId(id);

        return beneficioMapper.toResponse(beneficio);
    }

    @Override
    public BeneficioResponse crear(BeneficioRequest request) {

        String nombreNormalizado = normalizarTexto(request.getNombre());
        String descripcionNormalizada = normalizarTexto(request.getDescripcion());

        /*
         * Primero normalizamos el texto y después verificamos
         * si el beneficio ya existe.
         */
        if (beneficioRepository.existsByNombreIgnoreCase(nombreNormalizado)) {

            throw new DatoDuplicadoException(
                    "Ya existe un beneficio con el nombre: "
                            + nombreNormalizado
            );
        }

        Beneficio beneficio = beneficioMapper.toEntity(request);

        /*
         * Guardamos los valores normalizados.
         */
        beneficio.setNombre(nombreNormalizado);
        beneficio.setDescripcion(descripcionNormalizada);

        /*
         * Si activo fue omitido del JSON, BeneficioRequest
         * lo habrá establecido en true.
         */
        beneficio.setActivo(request.getActivo());

        Beneficio guardado = beneficioRepository.save(beneficio);

        return beneficioMapper.toResponse(guardado);
    }

    @Override
    public BeneficioResponse actualizar(
            Long id,
            BeneficioRequest request
    ) {

        Beneficio beneficio = buscarPorId(id);

        String nombreNormalizado = normalizarTexto(request.getNombre());
        String descripcionNormalizada = normalizarTexto(request.getDescripcion());

        /*
         * Buscar si ya existe otro beneficio con ese nombre.
         */
        Optional<Beneficio> beneficioExistente =
                beneficioRepository.findByNombreIgnoreCase(nombreNormalizado);

        /*
         * Si existe y no es el mismo registro que estamos actualizando,
         * entonces es un duplicado.
         */
        if (beneficioExistente.isPresent()
                && !beneficioExistente.get()
                .getIdBeneficio()
                .equals(id)) {

            throw new DatoDuplicadoException(
                    "Ya existe otro beneficio con el nombre: "
                            + nombreNormalizado
            );
        }

        /*
         * Actualizamos normalmente utilizando el mapper.
         */
        beneficioMapper.updateEntity(beneficio, request);

        /*
         * Sobrescribimos nombre y descripción con los
         * valores normalizados.
         */
        beneficio.setNombre(nombreNormalizado);
        beneficio.setDescripcion(descripcionNormalizada);
        beneficio.setActivo(request.getActivo());

        Beneficio actualizado =
                beneficioRepository.save(beneficio);

        return beneficioMapper.toResponse(actualizado);
    }

    @Override
    public void eliminar(Long id) {

        Beneficio beneficio = buscarPorId(id);

        beneficioRepository.delete(beneficio);
    }

    private Beneficio buscarPorId(Long id) {

        return beneficioRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "No se encontró el beneficio con id: " + id
                        )
                );
    }

    /*
     * Elimina espacios al inicio y al final.
     *
     * También convierte varios espacios consecutivos
     * dentro del texto en un único espacio.
     *
     * Ejemplo:
     *
     * "  Fortalece   el   sistema  "
     *
     * se convierte en:
     *
     * "Fortalece el sistema"
     */
    private String normalizarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        return texto
                .trim()
                .replaceAll("\\s+", " ");
    }
}
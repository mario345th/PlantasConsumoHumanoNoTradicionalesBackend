package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaActualizarRequest;
import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaRequest;
import sv.edu.ues.fmp.flora.dto.response.EpocaCosechaResponse;
import sv.edu.ues.fmp.flora.entity.EpocaCosecha;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.EpocaCosechaMapper;
import sv.edu.ues.fmp.flora.repository.EpocaCosechaRepository;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.service.EpocaCosechaService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

@Service
@RequiredArgsConstructor
public class EpocaCosechaServiceImpl implements EpocaCosechaService {

    private final EpocaCosechaRepository epocaCosechaRepository;
    private final EspecieParteComestibleRepository parteComestibleRepository;
    private final EpocaCosechaMapper epocaCosechaMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<EpocaCosechaResponse> listarPorParte(
            Long idEspecieParte,
            boolean soloActivas
    ) {
        buscarParteOFallar(idEspecieParte);

        List<EpocaCosecha> epocas = soloActivas
                ? epocaCosechaRepository
                .findByEspecieParteComestibleIdEspecieParteAndActivaTrueOrderByMesInicioAscMesFinAsc(
                        idEspecieParte
                )
                : epocaCosechaRepository
                .findByEspecieParteComestibleIdEspecieParteOrderByMesInicioAscMesFinAsc(
                        idEspecieParte
                );

        return epocas.stream()
                .map(epocaCosechaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EpocaCosechaResponse obtenerPorId(Long id) {
        EpocaCosecha entidad = buscarEpocaOFallar(id);

        // La epoca cuelga de una parte comestible, que cuelga de la especie: si
        // esa ficha no se puede ver, esto se responde como lo que no existe.
        if (!visibilidadDeEspecie.puedeVer(
                entidad.getEspecieParteComestible().getEspecie().getIdEspecie())) {

            throw noEncontrada(id);
        }

        return epocaCosechaMapper.toResponse(entidad);
    }

    /** El 404 del recurso, en un solo sitio para que las dos salidas coincidan. */
    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la época de cosecha con id " + id);
    }

    @Override
    @Transactional
    public EpocaCosechaResponse crear(EpocaCosechaRequest request) {
        EspecieParteComestible parte =
                buscarParteOFallar(request.getIdEspecieParte());

        verificarParteActiva(parte);
        verificarPeriodoDisponible(
                parte.getIdEspecieParte(),
                request.getMesInicio(),
                request.getMesFin(),
                null
        );

        EpocaCosecha nueva = epocaCosechaMapper.toEntity(request, parte);
        EpocaCosecha guardada = epocaCosechaRepository.save(nueva);

        return epocaCosechaMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public EpocaCosechaResponse actualizar(
            Long id,
            EpocaCosechaActualizarRequest request
    ) {
        EpocaCosecha entidad = buscarEpocaOFallar(id);

        verificarPeriodoDisponible(
                entidad.getEspecieParteComestible().getIdEspecieParte(),
                request.getMesInicio(),
                request.getMesFin(),
                id
        );

        epocaCosechaMapper.updateEntity(entidad, request);
        return epocaCosechaMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        buscarEpocaOFallar(id).setActiva(false);
    }

    @Override
    @Transactional
    public EpocaCosechaResponse activar(Long id) {
        EpocaCosecha entidad = buscarEpocaOFallar(id);

        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return epocaCosechaMapper.toResponse(entidad);
        }

        verificarParteActiva(entidad.getEspecieParteComestible());
        entidad.setActiva(true);

        return epocaCosechaMapper.toResponse(entidad);
    }

    private void verificarPeriodoDisponible(
            Long idEspecieParte,
            Short mesInicio,
            Short mesFin,
            Long idExcluir
    ) {
        epocaCosechaRepository
                .findByEspecieParteComestibleIdEspecieParteAndMesInicioAndMesFin(
                        idEspecieParte,
                        mesInicio,
                        mesFin
                )
                .filter(existente ->
                        !existente.getIdEpocaCosecha().equals(idExcluir))
                .ifPresent(existente -> {
                    throw new RecursoDuplicadoException(
                            "La parte comestible ya tiene registrada una época "
                                    + "de cosecha con esos meses, aunque esté "
                                    + "desactivada."
                    );
                });
    }

    private void verificarParteActiva(EspecieParteComestible parte) {
        if (!Boolean.TRUE.equals(parte.getActiva())) {
            throw new EstadoInvalidoException(
                    "La parte comestible con id "
                            + parte.getIdEspecieParte()
                            + " está desactivada."
            );
        }
    }

    private EspecieParteComestible buscarParteOFallar(Long id) {
        validarId(id);

        return parteComestibleRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la parte comestible con id " + id
                ));
    }

    private EpocaCosecha buscarEpocaOFallar(Long id) {
        validarId(id);

        return epocaCosechaRepository.findById(id)
                .orElseThrow(() -> noEncontrada(id));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException(
                    "El id debe ser un valor positivo"
            );
        }
    }
}

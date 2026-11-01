package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieParteComestibleActualizarRequest;
import sv.edu.ues.fmp.flora.dto.request.EspecieParteComestibleRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieParteComestibleResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.PartePlanta;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.EspecieParteComestibleMapper;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.PartePlantaRepository;
import sv.edu.ues.fmp.flora.service.EspecieParteComestibleService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

/**
 * Implementacion de la logica de negocio de las partes comestibles.
 * <p>
 * Reglas que viven aqui:
 * <ul>
 *   <li><strong>R1</strong>: la combinacion especie + parte es unica en todo el
 *       historial, activa o no ({@code uk_especie_parte} no es parcial). Si ya
 *       existe desactivada, el mensaje indica como reactivarla.</li>
 *   <li><strong>R2</strong>: la parte asociada no cambia despues de creada; el
 *       PUT solo edita descripcion y advertencias.</li>
 *   <li><strong>R3</strong>: la especie debe existir y estar activa.</li>
 *   <li><strong>R4</strong>: la parte del catalogo debe existir y estar
 *       activa.</li>
 *   <li><strong>R5</strong>: la baja es solo logica; seis tablas referencian
 *       este registro.</li>
 *   <li><strong>R6</strong>: reactivar exige que la parte del catalogo siga
 *       activa (misma regla R4).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EspecieParteComestibleServiceImpl implements EspecieParteComestibleService {

    private final EspecieParteComestibleRepository especieParteComestibleRepository;
    private final EspecieRepository especieRepository;
    private final PartePlantaRepository partePlantaRepository;
    private final EspecieParteComestibleMapper especieParteComestibleMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<EspecieParteComestibleResponse> listarPorEspecie(Long idEspecie, boolean soloActivas) {
        if (!especieRepository.existsById(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe la especie con id " + idEspecie);
        }

        List<EspecieParteComestible> partes = soloActivas
                ? especieParteComestibleRepository
                        .findByEspecieIdEspecieAndActivaTrueOrderByPartePlantaNombreAsc(idEspecie)
                : especieParteComestibleRepository
                        .findByEspecieIdEspecieOrderByPartePlantaNombreAsc(idEspecie);

        return partes.stream().map(especieParteComestibleMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EspecieParteComestibleResponse obtenerPorId(Long id) {
        return especieParteComestibleMapper.toResponse(buscarVisibleOFallar(id));
    }

    /**
     * La parte comestible, pero solo si la especie a la que pertenece se puede
     * ver. Si no, el mismo 404 que un id inexistente, para que no se distingan.
     */
    private EspecieParteComestible buscarVisibleOFallar(Long id) {
        EspecieParteComestible entidad = buscarOFallar(id);

        if (!visibilidadDeEspecie.puedeVer(entidad.getEspecie().getIdEspecie())) {
            throw noEncontrada(id);
        }

        return entidad;
    }

    @Override
    @Transactional
    public EspecieParteComestibleResponse crear(Long idEspecie, EspecieParteComestibleRequest request) {
        // R3
        Especie especie = especieRepository.findById(idEspecie)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la especie con id " + idEspecie));
        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException(
                    "No se pueden agregar partes a una especie desactivada.");
        }

        // R4
        PartePlanta parte = partePlantaRepository.findById(request.getIdPartePlanta())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la parte de planta con id " + request.getIdPartePlanta()));
        verificarParteActiva(parte);

        // R1: se busca tambien entre las desactivadas porque el UNIQUE no es parcial
        especieParteComestibleRepository
                .findByEspecieIdEspecieAndPartePlantaIdPartePlanta(idEspecie, parte.getIdPartePlanta())
                .ifPresent(existente -> {
                    if (Boolean.TRUE.equals(existente.getActiva())) {
                        throw new RecursoDuplicadoException(
                                "La especie '" + especie.getNombreCientifico()
                                        + "' ya tiene registrada la parte '" + parte.getNombre() + "'.");
                    }
                    throw new RecursoDuplicadoException(
                            "La parte '" + parte.getNombre() + "' ya está registrada para la especie '"
                                    + especie.getNombreCientifico() + "', pero está desactivada. "
                                    + "Actívela con PATCH /api/partes-comestibles/"
                                    + existente.getIdEspecieParte() + "/activar.");
                });

        EspecieParteComestible nueva = especieParteComestibleMapper.toEntity(request, especie, parte);
        return especieParteComestibleMapper.toResponse(especieParteComestibleRepository.save(nueva));
    }

    @Override
    @Transactional
    public EspecieParteComestibleResponse actualizar(Long id, EspecieParteComestibleActualizarRequest request) {
        EspecieParteComestible entidad = buscarOFallar(id);

        // R2: el DTO no trae la parte, asi que no hay nada que validar sobre ella.
        // Sin save(): la entidad esta gestionada y el UPDATE sale por dirty checking.
        especieParteComestibleMapper.updateEntity(entidad, request);
        return especieParteComestibleMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        EspecieParteComestible entidad = buscarOFallar(id);
        // R5: solo baja logica. Idempotente: si ya estaba desactivada no cambia nada.
        entidad.setActiva(false);
    }

    @Override
    @Transactional
    public EspecieParteComestibleResponse activar(Long id) {
        EspecieParteComestible entidad = buscarOFallar(id);

        // Idempotente
        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return especieParteComestibleMapper.toResponse(entidad);
        }

        // R6: no se revive una parte que el catalogo ya retiro
        verificarParteActiva(entidad.getPartePlanta());

        entidad.setActiva(true);
        return especieParteComestibleMapper.toResponse(entidad);
    }

    /** Regla R4, compartida por la creacion y la reactivacion (R6). */
    private void verificarParteActiva(PartePlanta parte) {
        if (!Boolean.TRUE.equals(parte.getActivo())) {
            throw new EstadoInvalidoException(
                    "La parte '" + parte.getNombre() + "' está desactivada en el catálogo.");
        }
    }

    private EspecieParteComestible buscarOFallar(Long id) {
        return especieParteComestibleRepository.findById(id)
                .orElseThrow(() -> noEncontrada(id));
    }

    /** El 404 del recurso, en un solo sitio. */
    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la parte comestible con id " + id);
    }
}

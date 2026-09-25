package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sv.edu.ues.fmp.flora.dto.request.EspecieHabitatRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieHabitatResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.EspecieHabitat;
import sv.edu.ues.fmp.flora.entity.Habitat;
import sv.edu.ues.fmp.flora.entity.enums.EspecieHabitatId;
import sv.edu.ues.fmp.flora.exception.IdInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.EspecieHabitatMapper;
import sv.edu.ues.fmp.flora.repository.EspecieHabitatRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.HabitatRepository;
import sv.edu.ues.fmp.flora.service.EspecieHabitatService;

@Service
@RequiredArgsConstructor
public class EspecieHabitatServiceImpl implements EspecieHabitatService {

    private final EspecieHabitatRepository especieHabitatRepository;
    private final EspecieRepository especieRepository;
    private final HabitatRepository habitatRepository;
    private final EspecieHabitatMapper especieHabitatMapper;

    /**
     * Consulta todas las relaciones y las convierte a DTOs dentro de una
     * transacción de lectura, donde pueden cargarse las asociaciones LAZY.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EspecieHabitatResponse> listarTodos() {
        return especieHabitatRepository.findAll().stream()
                .map(especieHabitatMapper::toResponse).toList();
    }

    /**
     * Busca la relación utilizando ambos componentes de la clave y la convierte
     * a respuesta. Lanza RecursoNoEncontradoException cuando no existe.
     */
    @Override
    @Transactional(readOnly = true)
    public EspecieHabitatResponse obtenerPorId(Long idEspecie, Long idHabitat) {
        return especieHabitatMapper.toResponse(buscarOFallar(idEspecie, idHabitat));
    }

    /**
     * Verifica primero que la especie exista y consulta sus relaciones.
     * Así distingue un recurso inexistente de una lista legítimamente vacía.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EspecieHabitatResponse> listarPorEspecie(Long idEspecie) {
        if (!especieRepository.existsById(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe la especie con id " + idEspecie);
        }
        return especieHabitatRepository.findByEspecieIdEspecie(idEspecie).stream()
                .map(especieHabitatMapper::toResponse).toList();
    }

    /**
     * Verifica primero que el hábitat exista y consulta sus relaciones.
     * Así distingue un recurso inexistente de una lista legítimamente vacía.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EspecieHabitatResponse> listarPorHabitat(Long idHabitat) {
        if (!habitatRepository.existsById(idHabitat)) {
            throw new RecursoNoEncontradoException("No existe el hábitat con id " + idHabitat);
        }
        return especieHabitatRepository.findByHabitatIdHabitat(idHabitat).stream()
                .map(especieHabitatMapper::toResponse).toList();
    }

    /**
     * Valida los IDs, recupera ambos padres y rechaza una combinación duplicada.
     * El mapper construye la entidad y saveAndFlush ejecuta la inserción para
     * comprobar las restricciones de la base dentro de esta transacción.
     * Las violaciones de integridad se propagan al manejador global.
     */
    @Override
    @Transactional
    public EspecieHabitatResponse crear(EspecieHabitatRequest request) {
        validarIdsObligatorios(request);
        Especie especie = especieRepository.findById(request.getIdEspecie())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la especie con id " + request.getIdEspecie()));
        Habitat habitat = habitatRepository.findById(request.getIdHabitat())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el hábitat con id " + request.getIdHabitat()));
        EspecieHabitatId id = new EspecieHabitatId(request.getIdEspecie(), request.getIdHabitat());
        if (especieHabitatRepository.existsById(id)) {
            throw new RecursoDuplicadoException("Ya existe la relación entre la especie "
                    + request.getIdEspecie() + " y el hábitat " + request.getIdHabitat());
        }
        EspecieHabitat nueva = especieHabitatMapper.toEntity(request, especie, habitat);
        return especieHabitatMapper.toResponse(especieHabitatRepository.saveAndFlush(nueva));
    }

    /**
     * Comprueba que el request conserve la clave compuesta de la ruta y modifica
     * solo la observación. La entidad permanece gestionada: Hibernate guarda el
     * cambio al confirmar la transacción, sin necesitar otra llamada a save.
     */
    @Override
    @Transactional
    public EspecieHabitatResponse actualizar(
            Long idEspecie, Long idHabitat, EspecieHabitatRequest request) {
        validarIdsObligatorios(request);
        if (!Objects.equals(idEspecie, request.getIdEspecie())
                || !Objects.equals(idHabitat, request.getIdHabitat())) {
            throw new IdInvalidoException(
                    "Los IDs del cuerpo deben coincidir con la ruta. "
                            + "Para cambiar la especie o el hábitat, elimine la relación y cree otra.");
        }
        EspecieHabitat entidad = buscarOFallar(idEspecie, idHabitat);
        especieHabitatMapper.updateEntity(entidad, request);
        return especieHabitatMapper.toResponse(entidad);
    }

    /**
     * Busca la relación antes de eliminarla para informar si no existe.
     * Como las asociaciones no tienen cascada de eliminación, los padres se conservan.
     */
    @Override
    @Transactional
    public void eliminar(Long idEspecie, Long idHabitat) {
        especieHabitatRepository.delete(buscarOFallar(idEspecie, idHabitat));
    }

    /**
     * Construye la clave con ambos IDs y recupera la relación.
     * Lanza RecursoNoEncontradoException si esa combinación no está registrada.
     */
    private EspecieHabitat buscarOFallar(Long idEspecie, Long idHabitat) {
        return especieHabitatRepository.findById(new EspecieHabitatId(idEspecie, idHabitat))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la relación entre la especie " + idEspecie
                                + " y el hábitat " + idHabitat));
    }

    /**
     * Rechaza IDs nulos también cuando el servicio se invoca fuera del controlador,
     * donde no se ejecutaría la validación del cuerpo HTTP.
     */
    private void validarIdsObligatorios(EspecieHabitatRequest request) {
        if (request.getIdEspecie() == null || request.getIdHabitat() == null) {
            throw new IdInvalidoException("La especie y el hábitat son obligatorios");
        }
    }
}

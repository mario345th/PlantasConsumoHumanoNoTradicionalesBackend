package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoRequest;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoUpdateRequest;
import sv.edu.ues.fmp.flora.dto.response.PreparacionConsumoResponse;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.exception.*;
import sv.edu.ues.fmp.flora.mapper.PreparacionConsumoMapper;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.service.PreparacionConsumoService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

@Service
@RequiredArgsConstructor
public class PreparacionConsumoServiceImpl implements PreparacionConsumoService {
    private final PreparacionConsumoRepository repository;
    private final EspecieParteComestibleRepository parteRepository;
    private final PreparacionConsumoMapper mapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<PreparacionConsumoResponse> listar(boolean soloActivas) {
        List<PreparacionConsumo> registros = soloActivas
                ? repository.findByActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc()
                : repository.findAllByOrderByOrdenPresentacionAscIdPreparacionAsc();
        return registros.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreparacionConsumoResponse> listarPorParte(Long idParte, boolean soloActivas) {
        EspecieParteComestible parte = buscarParte(idParte);

        // La parte de una ficha sin publicar se responde como una parte que no
        // existe: el mismo 404 que acaba de lanzar buscarParte.
        if (!visibilidadDeEspecie.puedeVer(parte.getEspecie().getIdEspecie())) {
            throw parteNoEncontrada(idParte);
        }

        List<PreparacionConsumo> registros = soloActivas
                ? repository.findByEspecieParteComestibleIdEspecieParteAndActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc(idParte)
                : repository.findByEspecieParteComestibleIdEspecieParteOrderByOrdenPresentacionAscIdPreparacionAsc(idParte);
        return registros.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PreparacionConsumoResponse obtenerPorId(Long id) {
        PreparacionConsumo entidad = buscarOFallar(id);

        if (!visibilidadDeEspecie.puedeVer(
                entidad.getEspecieParteComestible().getEspecie().getIdEspecie())) {

            throw noEncontrada(id);
        }

        return mapper.toResponse(entidad);
    }

    /** Los 404 del modulo, cada uno en un solo sitio. */
    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la preparación con id " + id);
    }

    private static RecursoNoEncontradoException parteNoEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la parte comestible con id " + id);
    }

    @Override
    @Transactional
    public PreparacionConsumoResponse crear(PreparacionConsumoRequest request) {
        EspecieParteComestible parte = buscarParte(request.getIdEspecieParte());
        verificarParteActiva(parte);
        if (repository.existsByEspecieParteComestibleIdEspecieParteAndNombreIgnoreCase(
                request.getIdEspecieParte(), request.getNombre())) {
            throw duplicado();
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request, parte)));
    }

    @Override
    @Transactional
    public PreparacionConsumoResponse actualizar(Long id, PreparacionConsumoUpdateRequest request) {
        PreparacionConsumo entidad = buscarOFallar(id);
        EspecieParteComestible parte = buscarParte(request.getIdEspecieParte());
        boolean activa = request.getActiva() != null
                ? request.getActiva() : Boolean.TRUE.equals(entidad.getActiva());
        // Una preparacion inactiva puede corregirse sin reactivar su parte.
        if (activa) verificarParteActiva(parte);
        if (repository.existsByEspecieParteComestibleIdEspecieParteAndNombreIgnoreCaseAndIdPreparacionNot(
                request.getIdEspecieParte(), request.getNombre(), id)) {
            throw duplicado();
        }
        mapper.updateEntity(entidad, request, parte);
        entidad.setActiva(activa);
        return mapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        // No borra la fila ni modifica imagenes, videos o la parte comestible.
        buscarOFallar(id).setActiva(false);
    }

    private PreparacionConsumo buscarOFallar(Long id) {
        validarId(id, "preparación");
        return repository.findById(id).orElseThrow(() -> noEncontrada(id));
    }

    private EspecieParteComestible buscarParte(Long id) {
        validarId(id, "parte comestible");
        return parteRepository.findById(id).orElseThrow(() -> parteNoEncontrada(id));
    }

    private void verificarParteActiva(EspecieParteComestible parte) {
        if (!Boolean.TRUE.equals(parte.getActiva())) {
            throw new EstadoInvalidoException(
                    "La parte comestible está inactiva. No se puede guardar una preparación activa.");
        }
    }

    private void validarId(Long id, String recurso) {
        if (id == null || id <= 0) {
            throw new IdInvalidoException("El id de " + recurso + " debe ser positivo");
        }
    }

    private RecursoDuplicadoException duplicado() {
        return new RecursoDuplicadoException(
                "Ya existe una preparación con ese nombre para la parte comestible, incluso si está inactiva.");
    }
}

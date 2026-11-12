package sv.edu.ues.fmp.flora.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
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
import sv.edu.ues.fmp.flora.util.Restricciones;

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

        Fuente guardada = guardarConTituloUnico(nueva,
                "Ya existe una fuente con el título " + request.getTitulo());

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

        Fuente actualizada = guardarConTituloUnico(entidad,
                "Ya existe otra fuente con el título " + request.getTitulo());

        return fuenteMapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Fuente entidad = buscarOFallar(id);

        entidad.setActiva(false);

    }

    @Override
    @Transactional
    public FuenteResponse activar(Long id) {
        Fuente entidad = buscarOFallar(id);

        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return fuenteMapper.toResponse(entidad);
        }

        entidad.setActiva(true);
        return fuenteMapper.toResponse(entidad);
    }

    private Fuente buscarOFallar(Long id) {
        return fuenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una fuente con id " + id));
    }

    /**
     * Escribe la fuente traduciendo el choque con el indice unico del titulo
     * al mismo 409 que devuelven las comprobaciones de arriba.
     *
     * <h2>Que cubre que no cubran esas comprobaciones</h2>
     * {@code existsByTituloIgnoreCase} / {@code findByTituloIgnoreCase} se
     * consultan antes de escribir, asi que entre la consulta y el INSERT cabe
     * otra transaccion con el mismo titulo: las dos pasan la comprobacion y es
     * {@code uk_fuente_titulo_lower} la que rechaza a la segunda. Tambien
     * cubren de menos cuando una fila vieja quedo guardada con espacios
     * alrededor: la consulta compara el titulo completo, mientras que el indice
     * compara {@code lower(btrim(titulo))}, asi que el indice ve un duplicado
     * donde la consulta no veia ninguno.
     * <p>
     * En los dos casos, sin este catch la respuesta era el 409 generico del
     * {@code GlobalExceptionHandler} ("La operacion viola una restriccion de la
     * base de datos"), que no nombra el titulo que choco.
     *
     * <h2>Por que saveAndFlush y no save</h2>
     * El UPDATE de {@code actualizar} lo emitiria Hibernate al confirmar la
     * transaccion, es decir DESPUES de que este metodo termine: la excepcion no
     * se podria atrapar aqui y volveria a salir el mensaje generico. El flush
     * adelanta la escritura para que el choque ocurra donde se sabe explicar.
     */
    private Fuente guardarConTituloUnico(Fuente fuente, String mensajeDeConflicto) {
        try {
            return fuenteRepository.saveAndFlush(fuente);
        } catch (DataIntegrityViolationException choque) {
            if (Restricciones.violacionDe(choque, "uk_fuente_titulo_lower")) {
                throw new RecursoDuplicadoException(mensajeDeConflicto);
            }
            throw choque;
        }
    }

    /**
     * Normaliza el titulo y la url sobre el propio Request, antes de consultar
     * y de escribir.
     * <p>
     * El titulo se normaliza porque es el que lleva el indice unico
     * {@code uk_fuente_titulo_lower} sobre {@code lower(btrim(titulo))}: si se
     * guardara con espacios alrededor, la comprobacion de duplicados de arriba
     * --que compara el titulo completo-- y el indice dejarian de mirar lo
     * mismo.
     */
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
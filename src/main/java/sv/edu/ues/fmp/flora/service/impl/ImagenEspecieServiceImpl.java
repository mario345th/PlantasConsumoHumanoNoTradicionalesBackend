package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.ImagenEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.ImagenEspecieResponse;
import sv.edu.ues.fmp.flora.entity.*;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.ImagenEspecieMapper;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.ImagenEspecieRepository;
import sv.edu.ues.fmp.flora.service.ImagenEspecieService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

/** Gestiona imágenes, valida sus relaciones y coordina el índice parcial de principal activa. */
@Service
@RequiredArgsConstructor
@Transactional
public class ImagenEspecieServiceImpl implements ImagenEspecieService {
    private final ImagenEspecieRepository imagenEspecieRepository;
    private final EspecieRepository especieRepository;
    private final ValidadorParteEspecie validadorParteEspecie;
    private final ImagenEspecieMapper imagenEspecieMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    /** Distingue especie inexistente de un listado vacío; permite consultar el historial. */
    @Override
    @Transactional(readOnly = true)
    public List<ImagenEspecieResponse> listarPorEspecie(Long idEspecie, boolean soloActivas) {
        if (!especieRepository.existsById(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe la especie con id " + idEspecie);
        }
        List<ImagenEspecie> imagenes = soloActivas
                ? imagenEspecieRepository.findByEspecieIdEspecieAndActivaTrueOrderByIdImagenAsc(idEspecie)
                : imagenEspecieRepository.findByEspecieIdEspecieOrderByIdImagenAsc(idEspecie);
        return imagenes.stream().map(imagenEspecieMapper::toResponse).toList();
    }

    /** Consulta una imagen sin filtrar su estado. */
    @Override
    @Transactional(readOnly = true)
    public ImagenEspecieResponse obtenerPorId(Long id) {
        return imagenEspecieMapper.toResponse(buscarVisibleOFallar(id));
    }

    /**
     * La imagen, pero solo si la especie a la que pertenece se puede ver.
     * <p>
     * Cuando no, lanza el <strong>mismo</strong> 404 que {@link #buscarOFallar}
     * da para un id que no existe: los dos salen de {@link #noEncontrada} para
     * que sean identicos y la imagen de un borrador no se pueda distinguir de
     * una que no existe. Un 403 aqui confirmaria que ese id esta ocupado.
     */
    private ImagenEspecie buscarVisibleOFallar(Long id) {
        ImagenEspecie entidad = buscarOFallar(id);

        if (!visibilidadDeEspecie.puedeVer(entidad.getEspecie().getIdEspecie())) {
            throw noEncontrada(id);
        }

        return entidad;
    }

    /** Valida especie y parte antes de desplazar a la principal e insertar. */
    @Override
    public ImagenEspecieResponse crear(Long idEspecie, ImagenEspecieRequest request) {
        Especie especie = especieRepository.findById(idEspecie)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la especie con id " + idEspecie));
        verificarEspecieActiva(especie);
        EspecieParteComestible parte = validadorParteEspecie.resolver(idEspecie, request.getIdEspecieParte());
        if (Boolean.TRUE.equals(request.getEsPrincipal())) {
            desmarcarPrincipalActual(idEspecie);
        }
        ImagenEspecie nueva = imagenEspecieMapper.toEntity(request, especie, parte);
        return imagenEspecieMapper.toResponse(imagenEspecieRepository.save(nueva));
    }

    /** Solo desplaza a la principal en la transición false a true. */
    @Override
    public ImagenEspecieResponse actualizar(Long id, ImagenEspecieRequest request) {
        ImagenEspecie entidad = buscarOFallar(id);
        verificarEspecieActiva(entidad.getEspecie());
        Long idEspecie = entidad.getEspecie().getIdEspecie();
        EspecieParteComestible parte = validadorParteEspecie.resolver(idEspecie, request.getIdEspecieParte());
        boolean promocion = Boolean.TRUE.equals(request.getEsPrincipal())
                && !Boolean.TRUE.equals(entidad.getEsPrincipal());
        if (promocion) {
            verificarImagenActiva(entidad);
            desmarcarPrincipalActual(idEspecie);
        }
        // Entidad gestionada: Hibernate emite el UPDATE sin llamar a save().
        imagenEspecieMapper.updateEntity(entidad, request, parte);
        return imagenEspecieMapper.toResponse(entidad);
    }

    /** Baja idempotente; desactiva la imagen y desmarca su condición de principal. */
    @Override
    public void desactivar(Long id) {
        ImagenEspecie entidad = buscarOFallar(id);
        entidad.setActiva(false);
        entidad.setEsPrincipal(false);
    }

    /** Reactiva de forma idempotente como no-principal, sin desplazar a la principal activa. */
    @Override
    public ImagenEspecieResponse activar(Long id) {
        ImagenEspecie entidad = buscarOFallar(id);
        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return imagenEspecieMapper.toResponse(entidad);
        }
        verificarEspecieActiva(entidad.getEspecie());
        validadorParteEspecie.resolver(entidad.getEspecie().getIdEspecie(),
                entidad.getEspecieParte() == null ? null : entidad.getEspecieParte().getIdEspecieParte());
        entidad.setEsPrincipal(false);
        entidad.setActiva(true);
        return imagenEspecieMapper.toResponse(entidad);
    }

    /** Repetir la promoción no cambia la principal ni la desmarca a sí misma. */
    @Override
    public ImagenEspecieResponse marcarComoPrincipal(Long id) {
        ImagenEspecie entidad = buscarOFallar(id);
        // El estado de la especie se comprueba antes del atajo de
        // idempotencia: con la especie inactiva el resto de operaciones ya se
        // rechazaban, pero volver a marcar como principal una imagen que ya lo
        // era respondia 200 (hallazgo IM-H01 del 3er informe de QA).
        verificarEspecieActiva(entidad.getEspecie());
        verificarImagenActiva(entidad);
        if (Boolean.TRUE.equals(entidad.getEsPrincipal())) {
            return imagenEspecieMapper.toResponse(entidad);
        }
        desmarcarPrincipalActual(entidad.getEspecie().getIdEspecie());
        entidad.setEsPrincipal(true);
        return imagenEspecieMapper.toResponse(entidad);
    }

    /** Libera el índice parcial antes del INSERT o UPDATE que promueve otra imagen. */
    private void desmarcarPrincipalActual(Long idEspecie) {
        imagenEspecieRepository.findByEspecieIdEspecieAndEsPrincipalTrueAndActivaTrue(idEspecie)
                .ifPresent(anterior -> {
                    anterior.setEsPrincipal(false);
                    // No se llama a save(): anterior es una entidad gestionada.
                    // Hibernate pone los INSERT antes de los UPDATE y tampoco garantiza
                    // el orden entre dos UPDATE. El índice parcial no es DEFERRABLE:
                    // PostgreSQL lo comprueba sentencia a sentencia. flush() adelanta
                    // la desmarcación antes de guardar o promover la nueva principal.
                    imagenEspecieRepository.flush();
                });
    }

    /** Resuelve el registro o devuelve el 404 de negocio. */
    private ImagenEspecie buscarOFallar(Long id) {
        return imagenEspecieRepository.findById(id)
                .orElseThrow(() -> noEncontrada(id));
    }

    /** El 404 del recurso, en un solo sitio para que no se separe de si mismo. */
    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la imagen con id " + id);
    }

    /** Las nuevas asociaciones y modificaciones requieren una especie activa. */
    private void verificarEspecieActiva(Especie especie) {
        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException("La especie '" + especie.getNombreCientifico()
                    + "' está desactivada; no se pueden registrar o promover sus imágenes.");
        }
    }

    /** Una imagen desactivada conserva su historial, pero no puede promocionarse. */
    private void verificarImagenActiva(ImagenEspecie imagen) {
        if (!Boolean.TRUE.equals(imagen.getActiva())) {
            throw new EstadoInvalidoException("Una imagen desactivada no puede marcarse como principal.");
        }
    }
}
